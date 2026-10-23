"""Independently verify product completion atomicity across the real driver commit boundary."""
import json
import uuid
from pathlib import Path

from d15_combination_runtime import digest
from d13_successor_fact_consume import rows
from verify_d13_restored_closed import load


def observations(attempt):
    private = attempt / 'private'
    home = private / 'restored-bound-commit'
    before, after = [load(home / name) for name in ('jdbc-before-commit.json', 'jdbc-after-commit-before-ack.json')]
    observers = [load(private / ('commit-' + phase + '-observer.json')) for phase in ('before', 'after')]
    request = load(private / 'commit-request.json')
    for committed, inner, outer in zip((False, True), (before, after), observers, strict=True):
        assert inner['job'] == outer['job'] == request['id']
        assert inner['pid'] == load(home / 'worker-ready.json')['pid'] != outer['pid']
        assert inner['syntheticAckLoss'] is True
        assert inner['driverCommitReturned'] is committed and outer['driverCommitReturned'] is committed
        assert inner['row']['status'] == 'SUCCEEDED' and inner['auditCount'] == 1
        assert outer['row']['status'] == ('SUCCEEDED' if committed else 'RUNNING')
        assert outer['auditCount'] == int(committed) and inner['row']['attempts'] == outer['row']['attempts'] == 1
        assert inner['database']['connectionId'] != outer['database']['connectionId']
        for fact in (inner, outer):
            identity = {key: value for key, value in fact['database'].items() if key != 'connectionId'}
            assert identity == load(private / 'target-identity.json')
    assert before['database']['connectionId'] == after['database']['connectionId']
    assert json.loads(before['row']['payload']) == json.loads(after['row']['payload']) == json.loads(observers[1]['row']['payload'])
    assert json.loads(observers[0]['row']['payload'])['artifact'] is None
    return request['id'], home


def facts(attempt, job):
    private = attempt / 'private'
    before, after = [load(private / ('commit-visible-' + name + '.json')) for name in ('before', 'after')]
    assert before['files'] == after['files'] and before['schema'].keys() == after['schema'].keys()
    changed = {name for name in before['tables'] if before['tables'][name] != after['tables'][name]}
    assert changed == {'background_job', 'operation_event'}
    assert before['flywayHistory'] == after['flywayHistory']
    jobs = [{str(uuid.UUID(bytes=row['id'])): row for row in rows(value, 'background_job')} for value in (before, after)]
    assert len(jobs[0]) == len(jobs[1]) == 5 and jobs[0].keys() == jobs[1].keys()
    assert {key: value for key, value in jobs[0].items() if key != job} == {key: value for key, value in jobs[1].items() if key != job}
    first, last = (items[job] for items in jobs)
    assert first['status'] == b'RUNNING' and last['status'] == b'SUCCEEDED'
    assert first['attempt_count'] == last['attempt_count'] == b'1'
    changed_columns = {key for key in first if first[key] != last[key]}
    assert changed_columns <= {'status', 'payload', 'lease_owner', 'lease_token', 'lease_until', 'updated_at'}
    assert first['lease_owner'] and first['lease_token'] and first['lease_until']
    assert last['lease_owner'] is last['lease_token'] is last['lease_until'] is None
    original, current = [rows(value, 'operation_event') for value in (before, after)]
    added = [row for row in current if row not in original]
    assert len(current) == len(original) + 1 and len(added) == 1 and all(row in current for row in original)
    event = added[0]
    assert event['event_type'] == b'REPORT_EXPORT_GENERATED' and event['request_id'].decode() == 'r08:' + job
    payload = json.loads(last['payload']); artifact = payload.pop('artifact')
    assert payload == {key: value for key, value in json.loads(first['payload']).items() if key != 'artifact'}
    report = attempt / 'restored/storage/exports/attempts' / job / artifact['token'] / 'report.xlsx'
    assert digest(report) == artifact['sha256'] and report.stat().st_size == artifact['bytes']
    details = json.loads(event['details'])
    assert details == dict(jobId=job, sha256=artifact['sha256'], bytes=artifact['bytes'])
    assert after == load(private / 'refusal-before.json') == load(private / 'refusal-after.json')
    return dict(tables=len(before['tables']), changedTables=sorted(changed),
                changedColumns=sorted(changed_columns), artifactSha256=artifact['sha256'], exactSingleAudit=True)


def commit(attempt):
    job, home = observations(attempt)
    proof = facts(attempt, job)
    held = load(home / 'worker-commit-held.json')
    result = load(home / 'worker-commit-exit.json')
    assert result['pid'] == held['pid'] and result['actualExitCode'] == 70
    assert not result['aliveAfter'] and not result['normalShutdown']
    assert load(home / 'owner-before.json')['lifecycleLockHeld'] is True
    assert load(home / 'owner-after.json')['lifecycleLockHeld'] is False
    assert not (home / 'worker-closed.marker').exists() and (home / 'api-closed.marker').is_file()
    assert (home / 'api-live-accepted.marker').is_file()
    assert load(home / 'api-commit-refused.json') == dict(pid=load(home / 'api-ready.json')['pid'],
        admissionCode='RECOVERY_LEASE_FENCED', effectCode='RECOVERY_LEASE_FENCED', liveRoots=0)
    output = (home / 'worker.stdout').read_text('utf-8') + (home / 'worker.stderr').read_text('utf-8')
    for code in ('FENCE_F1_PRIME_CONFIRMED', 'FENCE_F2_AUDIT_CONFIRMED', 'FENCE_F3_RELEASE_CONFIRMED', 'FENCE_F4_WRITERS_RETAINED'):
        assert code in output
    return dict(**proof, id=job, realProductCompletionCommit=True, syntheticAckLoss=True,
                actualProductHaltExit=70, independentBeforeAfterVisibility=True)


def http(attempt):
    results = {}
    for phase in ('pending', 'regenerated', 'commit'):
        statuses, codes = [], []
        for path in sorted((attempt / ('private/http-' + phase)).glob('*.http.json')):
            fact = load(path); body = path.with_name(path.name.replace('.http.json', '.body'))
            assert fact['status'] == fact['expected'] and digest(body) == fact['sha256']
            statuses.append(fact['status'])
            if fact['status'] == 503:
                codes.append(load(body)['code'])
        assert statuses
        results[phase] = dict(statuses=statuses, codes=codes)
    assert results['commit']['codes'] == ['RECOVERY_LEASE_OUTSTANDING'] + ['RECOVERY_LEASE_FENCED'] * 5
    assert results['commit']['statuses'][-6:] == [503] * 6
    return results
