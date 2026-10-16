"""Transaction consumer; writes only newly restored synthetic run copies."""
from contextlib import closing
from pathlib import Path
import uuid

from .catalog import OWNED, schema, validate
from .codec import canonical, digest, parse, require
from .references import selection
from .store import connect, facts, healthy, location, read, restore, write_new


def prepare(spec_path):
    spec = parse(read(spec_path))
    require(spec['scope'] == 'SYNTHETIC_GENERATED_ONLY' and spec['production'] is False,
            'Explicit synthetic-only declaration required')
    source = location(spec['source'])
    require(digest(read(source)) == spec['sourceSha256'], 'Source hash mismatch')
    require(spec['schemaSha256'] == schema()[1], 'Reviewed schema hash mismatch')
    run = location(Path(spec_path).absolute().parent / ('retention-' + str(uuid.uuid4())))
    require(len(str(run)) <= 190, 'Use a shorter evidence parent for Windows-safe attempt paths')
    run.mkdir()
    restore(source, run / 'backup.sqlite')
    restore(run / 'backup.sqlite', run / 'candidate.sqlite')
    with closing(connect(run / 'backup.sqlite')) as db:
        validate(db, spec.get('triggerProfile', 'SYNTHETIC_SCHEMA_ONLY'))
        before = facts(db)
        selected = selection(db, spec)
    plan = dict(spec=spec, runId=run.name, before=before, selected=selected,
                backupSha256=digest(read(run / 'backup.sqlite')))
    plan_sha = write_new(run / 'plan.json', plan)
    write_new(run / 'permit.json', dict(planSha256=plan_sha, runId=run.name, allowed=False))
    require(digest(read(source)) == spec['sourceSha256'], 'Source changed during restore')
    return dict(run=str(run), planSha256=plan_sha, restoredFactsSha256=digest(canonical(before)))


def expected_after(plan):
    return {table: [r for r in values if r not in plan['selected'].get(table, [])]
            for table, values in plan['before'].items()}


def permit(run, plan_sha):
    value = parse(read(run / 'permit.json'))
    require(value.get('allowed') is True and
            value == dict(planSha256=plan_sha, runId=run.name, allowed=True),
            'Synthetic permit absent, revoked or mismatched')


def transaction(db, plan, control):
    run, plan_sha = control
    db.execute('BEGIN IMMEDIATE')
    try:
        permit(run, plan_sha)  # Deliberately read after the actual SQLite lock wait.
        validate(db, plan['spec'].get('triggerProfile', 'SYNTHETIC_SCHEMA_ONLY'))
        current, after = facts(db), expected_after(plan)
        if current == after:
            db.execute('ROLLBACK')
            return 'REPLAY_NO_WRITE'
        require(current == plan['before'], 'Candidate facts changed')
        require(selection(db, plan['spec']) == plan['selected'], 'Reference closure changed')
        delete(db, plan)
        require(facts(db) == after, 'Unrelated facts changed')
        healthy(db)
        permit(run, plan_sha)
        db.execute('COMMIT')
        return 'COMMITTED_SYNTHETIC_ONLY'
    except BaseException:
        if db.in_transaction:
            db.execute('ROLLBACK')
        raise


def delete(db, plan):
    for table in OWNED:
        field = 'submissionId' if table == 'local_submission_archive' else 'assignmentId'
        value = plan['spec']['submissionId'] if field == 'submissionId' else parse(
            plan['selected']['local_submission'][0])['assignmentId']
        cursor = db.execute('DELETE FROM "' + table + '" WHERE "' + field + '"=?', (value,))
        require(cursor.rowcount == len(plan['selected'][table]), 'Deleted row count mismatch')


def consume(run_path, expected_sha, enabled=False):
    require(enabled is True, 'Default-off: explicit isolated deletion enable flag required')
    run = location(run_path)
    raw = read(run / 'plan.json')
    require(digest(raw) == expected_sha, 'Plan hash mismatch')
    plan = parse(raw)
    require(plan['runId'] == run.name and plan['spec']['production'] is False and
            plan['spec']['scope'] == 'SYNTHETIC_GENERATED_ONLY', 'Wrong synthetic run identity')
    verify_backup(run, plan)
    attempt = run / ('attempt-' + str(uuid.uuid4()) + '.json')
    write_new(attempt, dict(state='STARTED', planSha256=expected_sha, databaseMayHaveCommitted=True))
    with closing(connect(run / 'candidate.sqlite', True)) as db:
        state = transaction(db, plan, (run, expected_sha))
    result = dict(state=state, planSha256=expected_sha, localDeletionAllowed=False,
                  candidateSha256=digest(read(run / 'candidate.sqlite')),
                  factsSha256=digest(canonical(expected_after(plan))))
    write_new(attempt.with_suffix('.result.json'), result)
    return result


def verify_backup(run, plan):
    require(digest(read(run / 'backup.sqlite')) == plan['backupSha256'], 'Backup changed or missing')
    require(digest(read(plan['spec']['source'])) == plan['spec']['sourceSha256'], 'Source changed')
    with closing(connect(run / 'backup.sqlite')) as backup:
        healthy(backup)
        require(facts(backup) == plan['before'], 'Backup facts changed')
        require(selection(backup, plan['spec']) == plan['selected'], 'Backup closure differs')
