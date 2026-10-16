"""Exact database/file delta checks for the actual older scheduled worker."""
from collections import Counter
import importlib.util
import json
import uuid

from d15_old_worker_lab import LAB, ROOT, fixture, compare, sql


def decoded(table, row):
    binary_types = {'binary', 'varbinary', 'blob', 'tinyblob', 'mediumblob', 'longblob'}
    return {name: (None if cell is None else cell.lower() if kind in binary_types
                   else bytes.fromhex(cell).decode('utf-8'))
            for name, kind, cell in zip(table['columns'], table['types'], row, strict=True)}


def added(before, after, table):
    assert before[table]['columns'] == after[table]['columns']
    assert before[table]['types'] == after[table]['types']
    assert compare.rows(before[table]) <= compare.rows(after[table]), table
    return [decoded(after[table], json.loads(row)) for row in
            (compare.rows(after[table]) - compare.rows(before[table])).elements()]


def events(before, after, reader, identity, job, counts):
    delta = added(before, after, 'operation_event')
    assert Counter(row['event_type'] for row in delta) == Counter(counts), delta
    for row in delta:
        assert row['actor_id'].lower() == reader['actor']
        details = json.loads(row['details'])
        if row['event_type'] == 'WEB_LOGIN':
            assert row['entity_type'] == 'USER' and row['entity_id'].lower() == reader['actor']
            assert details == {}
        else:
            assert row['entity_type'] == 'ORDER'
            assert row['entity_id'].lower() == uuid.UUID(identity['order']).hex
            assert details['jobId'] == str(job)
    return delta


def authentication(before, after, reader, count):
    sessions = added(before, after, 'spring_session')
    attrs = added(before, after, 'spring_session_attributes')
    assert len(sessions) == count and len(attrs) == count * 2
    assert all(row['PRINCIPAL_NAME'] == reader['username'] for row in sessions)
    identifiers = {row['PRIMARY_ID'] for row in sessions}
    assert len(identifiers) == count
    names = {'SPRING_SECURITY_CONTEXT',
             'org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN'}
    assert Counter((row['SESSION_PRIMARY_ID'], row['ATTRIBUTE_NAME']) for row in attrs) == \
        Counter((identifier, name) for identifier in identifiers for name in names)


def job_row(facts, job):
    table = facts['background_job']
    assert len(table['rows']) == 1
    row = decoded(table, table['rows'][0])
    assert row['id'].lower() == job.hex and row['job_type'] == 'REPORT_RENDER_V1'
    return row


def job_delta(queued, final, job):
    before, after = job_row(queued, job), job_row(final, job)
    assert before.keys() == after.keys()
    assert {name for name in before if before[name] != after[name]} == {
        'status', 'payload', 'attempt_count', 'updated_at'}
    assert before['status'] == 'PENDING' and before['attempt_count'] == '0'
    assert after['status'] == 'SUCCEEDED' and after['attempt_count'] == '1' and after['last_error'] is None
    for field in ('id', 'job_type', 'dedupe_key', 'max_attempts', 'created_at', 'recovery_epoch'):
        assert before[field] == after[field], field
    for field in ('lease_owner', 'lease_token', 'lease_until'):
        assert after[field] is None, field
    old_payload, payload = json.loads(before['payload']), json.loads(after['payload'])
    assert old_payload['artifact'] is None and payload['artifact']['bytes'] > 0
    artifact = payload.pop('artifact')
    old_payload.pop('artifact')
    assert payload == old_payload, 'Old worker must preserve new payload fields'
    return payload, artifact


def conservation(baseline, after, options):
    assert baseline.keys() == after.keys()
    mutable = {'background_job', 'operation_event', 'spring_session', 'spring_session_attributes'}
    for name in baseline:
        if name not in mutable:
            assert baseline[name] == after[name], name
    assert compare.schema('baseline') == compare.schema(options['label'])
    authentication(baseline, after, options['reader'], options['logins'])
    delta = events(baseline, after, options['reader'], options['identity'], options['job'], options['events'])
    old = json.loads((LAB / 'baseline-files.json').read_bytes())
    files = fixture.files()
    assert all(files[name] == row for name, row in old.items())
    return dict(tables=len(after), unchangedTables=len(after) - len(mutable),
                rows=sum(len(row['rows']) for row in after.values()), events=delta, preservedFiles=len(old))


def workbook(path, payload, artifact):
    source = ROOT / '.local/goal-20261016/d15-restored-worker01/xlsx_readback01.py'
    spec = importlib.util.spec_from_file_location('retained_xlsx_consumer', source)
    consumer = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(consumer)
    snapshot = LAB / 'storage/exports/snapshots' / (payload['snapshotHash'] + '.json')
    assert sql.digest(snapshot) == payload['snapshotHash']
    assert sql.digest(path) == artifact['sha256'] and path.stat().st_size == artifact['bytes']
    return dict(consumer=consumer.consume(path, json.loads(snapshot.read_bytes())),
                consumerSha256=sql.digest(source), snapshotSha256=sql.digest(snapshot))


def final_files(payload, artifact, job):
    old = json.loads((LAB / 'baseline-files.json').read_bytes())
    current = fixture.files()
    new = set(current) - set(old)
    prefix = f'storage/exports/attempts/{job}/{artifact["token"]}/'
    expected = {'storage/exports/snapshots/' + payload['snapshotHash'] + '.json',
                prefix + 'report.xlsx', prefix + 'renderer.log'}
    assert new == expected, new
    assert current[prefix + 'report.xlsx']['sha256'] == artifact['sha256']
    log = (LAB / prefix / 'renderer.log').read_bytes()
    assert b'Exception' not in log and b'ERROR' not in log
    assert b'report.xlsx / SOURCE_SNAPSHOT' in log
    return dict(added={name: current[name] for name in sorted(new)}, originalFiles=len(old))
