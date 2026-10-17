"""Conservation checks for two independent export jobs in one worker queue."""
from collections import Counter
import json
import uuid

from d15_old_worker_checks import added, authentication, decoded, workbook
from d15_old_worker_lab import LAB, fixture


def events(before, after, reader, identity, jobs, counts):
    delta = added(before, after, 'operation_event')
    assert Counter(row['event_type'] for row in delta) == Counter(counts), delta
    for row in delta:
        assert row['actor_id'].lower() == reader['actor']
        details = json.loads(row['details'])
        if row['event_type'] == 'WEB_LOGIN':
            assert row['entity_type'] == 'USER' and row['entity_id'].lower() == reader['actor']
            assert details == {}
            continue
        assert row['entity_type'] == 'ORDER'
        assert row['entity_id'].lower() == uuid.UUID(identity['order']).hex
        assert details.get('jobId') in {str(job) for job in jobs}
    return delta


def conservation(before, after, reader, identity, jobs, logins, counts):
    mutable = {'background_job', 'operation_event', 'spring_session', 'spring_session_attributes'}
    assert before.keys() == after.keys()
    for name in before:
        if name not in mutable:
            assert before[name] == after[name], name
    assert {name: (value['columns'], value['types']) for name, value in before.items()} == {
        name: (value['columns'], value['types']) for name, value in after.items()}
    authentication(before, after, reader, logins)
    return events(before, after, reader, identity, jobs, counts)


def job_rows(facts, jobs, succeeded):
    table = facts['background_job']
    assert len(table['rows']) == len(jobs)
    wanted = {job.hex for job in jobs}
    rows = {decoded(table, row)['id'].lower(): decoded(table, row) for row in table['rows']}
    assert set(rows) == wanted
    for job in jobs:
        row = rows[job.hex]
        assert row['job_type'] == 'REPORT_RENDER_V1'
        assert row['attempt_count'] == '1' if succeeded else row['attempt_count'] == '0'
        assert row['status'] == 'SUCCEEDED' if succeeded else row['status'] == 'PENDING'
        payload = json.loads(row['payload'])
        if succeeded:
            assert payload['artifact']['bytes'] > 0
        else:
            assert payload['artifact'] is None
    return rows


def artifacts(facts, jobs):
    rows = job_rows(facts, jobs, True)
    result = {}
    for job in jobs:
        payload = json.loads(rows[job.hex]['payload'])
        artifact = payload.pop('artifact')
        result[job] = payload, artifact
    return result


def files(before, after, jobs, artifacts):
    old = json.loads((LAB / (before + '-files.json')).read_bytes())
    current = fixture.files()
    assert all(current[name] == row for name, row in old.items())
    assert current == json.loads((LAB / (after + '-files.json')).read_bytes())
    new = set(current) - set(old)
    expected = {f'storage/exports/snapshots/{payload["snapshotHash"]}.json'
                for payload, _ in artifacts.values()}
    for job, (_, artifact) in artifacts.items():
        prefix = f'storage/exports/attempts/{job}/{artifact["token"]}/'
        expected.update({prefix + 'report.xlsx', prefix + 'renderer.log'})
        assert current[prefix + 'report.xlsx']['sha256'] == artifact['sha256']
        assert b'Exception' not in (LAB / (prefix + 'renderer.log')).read_bytes()
    assert new == expected


def downloads(stages, artifacts):
    for _, path, view, job in stages:
        payload, artifact = artifacts[job]
        assert view['sha256'] == artifact['sha256'] and view['bytes'] == artifact['bytes']
        workbook(path, payload, artifact)
