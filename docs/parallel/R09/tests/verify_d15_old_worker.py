"""Read-only audit of sealed 677 evidence; never starts a process or writes the lab."""
from collections import Counter
import json
from pathlib import Path
import re
import sys
import uuid

import d15_old_worker_lab as lab
import d15_old_worker_checks as checks
from verify_d15_program_rollback import count_tuples


def load(name):
    return json.loads((lab.LAB / name).read_bytes())


def sealed(number, directory):
    root = lab.ROOT / '.local' / f'e2a-cross-20260925-{number}'
    record = lab.ROOT / '.local/goal-20261017-resume' / directory
    manifest = json.loads((record / 'manifest01.json').read_bytes())
    assert manifest == json.loads((record / 'manifest02.json').read_bytes())
    actual = {p.relative_to(root).as_posix():
              dict(bytes=p.stat().st_size, sha256=lab.sql.digest(p))
              for p in root.rglob('*') if p.is_file()}
    assert actual == manifest, f'Sealed {number} evidence changed'
    return dict(files=len(actual), bytes=sum(row['bytes'] for row in actual.values()),
                manifestSha256=lab.sql.digest(record / 'manifest01.json'))


def http(label, statuses):
    directory = lab.LAB / 'private' / label
    metadata = sorted(directory.glob('*-http.json'))
    assert len(metadata) == len(statuses)
    responses, evidence = [], []
    for index, (path, expected) in enumerate(zip(metadata, statuses, strict=True), 1):
        assert path.name == f'{index:02}-http.json'
        row = json.loads(path.read_bytes())
        binary = row['path'].split('?')[0].endswith('/file')
        body = path.with_name(f'{index:02}' + ('.xlsx' if binary else '.json'))
        assert row['status'] == row['expected'] == expected
        assert body.stat().st_size == row['bytes'] and lab.sql.digest(body) == row['sha256']
        responses.append(None if binary else json.loads(body.read_bytes()))
        evidence.append(dict(index=index, status=expected, method=row['method'], sha256=row['sha256']))
    return responses, evidence


def http_business(job, artifact):
    queued, a = http('new-queued', [401, 200, 200, 200, 404, 200, 200, 409])
    old, b = http('old-read', [200] * 5)
    new, c = http('new-read', [200] * 6 + [409])
    assert queued[4]['code'] == 'WAREHOUSE_NOT_FOUND'
    assert queued[5]['data'] == queued[6]['data'] == load('queued-http-result.json')['view']
    assert queued[5]['data']['id'] == str(job) and queued[5]['data']['status'] == 'PENDING'
    assert queued[5]['data']['attempts'] == 0 and queued[5]['data']['downloadable'] is False
    assert queued[7]['code'] == new[6]['code'] == 'IDEMPOTENCY_CONFLICT'
    assert old[3]['data'] == new[3]['data'] == new[5]['data']
    for label in ('old-read', 'new-read'):
        result = load(label + '-result.json')
        assert result['view'] == old[3]['data'] and result['sha256'] == artifact['sha256']
        assert result['view']['bytes'] == artifact['bytes'] and result['view']['attempts'] == 1
        assert result['view']['status'] == 'SUCCEEDED' and result['view']['downloadable'] is True
    return dict(requests=20, stages=dict(queued=a, old=b, new=c))


def stages():
    scenario = load('private/scenario.json')
    reader, identity = scenario['reader'], scenario['identity']
    base, queued = load('baseline-facts.json'), load('queued-facts.json')
    job = uuid.UUID(load('queued-http-result.json')['view']['id'])
    options = dict(reader=reader, identity=identity, job=job)
    counts = {'WEB_LOGIN': 1, 'REPORT_EXPORT_REQUESTED': 1}
    result = {}
    for label, logins, extra in [('queued', 1, {}), ('worked', 1, {'REPORT_EXPORT_GENERATED': 1}),
                                ('old-read', 2, {'REPORT_EXPORT_DOWNLOADED': 1}),
                                ('new-read', 3, {'REPORT_EXPORT_DOWNLOADED': 2})]:
        counts = counts | extra | dict(WEB_LOGIN=logins)
        facts = load(label + '-facts.json')
        result[label] = checks.conservation(base, facts, options | dict(label=label, logins=logins, events=counts))
    worked = load('worked-facts.json')
    payload, artifact = checks.job_delta(queued, worked, job)
    for label in ('old-read', 'new-read'):
        assert load(label + '-facts.json')['background_job'] == worked['background_job']
        result[label]['xlsx'] = checks.workbook(lab.LAB / (label + '.xlsx'), payload, artifact)
    result['files'] = checks.final_files(payload, artifact, job)
    result['http'] = http_business(job, artifact)
    return result


def reader_delta():
    before, after = load('restored-facts.json'), load('baseline-facts.json')
    expected = {'app_user', 'role', 'role_permission', 'user_role', 'user_warehouse'}
    assert {name for name in before if before[name] != after[name]} == expected
    retained = load('reader-added-rows.json')
    for name in expected:
        assert lab.compare.rows(before[name]) <= lab.compare.rows(after[name])
        assert lab.compare.rows(after[name]) - lab.compare.rows(before[name]) == Counter([json.dumps(retained[name])])
    assert load('restored-files.json') == load('baseline-files.json')
    assert lab.compare.schema('restored') == lab.compare.schema('baseline')
    return dict(originalRows=sum(len(t['rows']) for t in before.values()), addedRows=5)


def dump_facts():
    raw = (lab.LAB / 'private/final.sql').read_bytes()
    tables = re.findall(rb'^CREATE TABLE \x60([A-Za-z0-9_]+)\x60', raw, re.M)
    facts, counts = load('new-read-facts.json'), Counter()
    assert len(tables) == len(facts) and {t.decode() for t in tables} == set(facts)
    for line in raw.splitlines():
        match = re.match(rb'INSERT INTO \x60([A-Za-z0-9_]+)\x60 \([^\r\n]+?\) VALUES (.*);$', line)
        if match:
            counts[match[1].decode()] += count_tuples(match[2])
    assert dict(counts) == {name: len(t['rows']) for name, t in facts.items() if t['rows']}
    assert load('dump-command.json')['exitCode'] == 0
    return dict(tables=len(tables), rows=sum(counts.values()), sha256=lab.sql.digest(lab.LAB / 'private/final.sql'))


def programs():
    assert load('mysql-stopped.json')['listener'] is False
    for label in ('new-queued', 'old-worker', 'old-read', 'new-read'):
        command, stopped = load(label + '-command.json'), load(label + '-stopped.json')
        assert command['pid'] == stopped['pid'] and stopped['httpListener'] is False
        assert load(label + '-remaining-java.json') == []
        jar = lab.JARS['old' if label.startswith('old') else 'new'][0]
        assert str(jar) in command['args']
        if label != 'old-worker':
            assert load(label + '-listener.json')['OwningProcess'] == command['pid']
    assert load('old-worker-command.json')['scheduledWorker'] is True
    assert 'BackgroundWorkerProcess' in (lab.LAB / 'old-worker.stdout').read_text('utf-8')
    assert 'SUCCEEDED' in (lab.LAB / 'old-worker.stdout').read_text('utf-8')
    return dict(pinnedPrograms=4, dedicatedProcessesDrained=True, driverExitCode=1)


def input_changes():
    original = load('input.json')
    changed = {name: dict(before=row, after=dict(bytes=(lab.ROOT / name).stat().st_size,
               sha256=lab.sql.digest(lab.ROOT / name))) for name, row in original.items()
               if lab.sql.digest(lab.ROOT / name) != row['sha256']}
    assert set(changed) == {'docs/parallel/R09/tests/d15_old_worker_checks.py'}
    return dict(originalInputs=len(original), unchangedInputs=len(original) - len(changed),
                correctedOfflineAuditOnly=changed, verifierSha256=lab.sql.digest(Path(__file__)))


def main():
    output = Path(sys.argv[1]).resolve()
    assert output.is_relative_to(lab.ROOT / '.local/goal-20261017-resume') and not output.exists()
    evidence = dict(
        failed676=sealed(676, 'd15-worker-failure676'),
        run677=sealed(677, 'd15-worker-run677-preserved'),
        source667=lab.fixture.original_files(), readerDelta=reader_delta(),
        stages=stages(), dump=dump_facts(), programs=programs(), inputs=input_changes())
    assert evidence['source667'] == load('source667-before.json')
    assert evidence['run677'] == sealed(677, 'd15-worker-run677-preserved')
    assert evidence['failed676'] == sealed(676, 'd15-worker-failure676')
    evidence.update(result='PASS_OFFLINE_AUDIT_AFTER_BUSINESS', scope='D15 older worker bounded L1 only',
                    driverExitCode=1, databaseRestarted=False, httpRepeated=False)
    output.write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(dict(result=evidence['result'], requests=20, dump=evidence['dump'],
                         driverExitCode=1, output=str(output)), indent=2))


if __name__ == '__main__':
    main()
