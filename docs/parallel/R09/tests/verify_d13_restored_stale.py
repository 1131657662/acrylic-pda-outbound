"""Read original restored stale refusals, whole snapshots and delivered files offline."""
import argparse
import base64
import json
from pathlib import Path
import uuid

from d13_export_file_consume import pdf, xlsx
from d15_combination_runtime import digest, save
from verify_d13_restored_scheduler import stable_schema


def load(path):
    return json.loads(path.read_bytes())


def conservation(attempt):
    before, stable, final = [load(attempt / name) for name in
                             ('restored-before.json', 'restored-stable.json', 'restored-final.json')]
    assert stable == final
    assert before == load(attempt / 'source-final.json') == load(attempt / 'private/archive-snapshot-facts.json')
    assert set(before['tables']) == set(final['tables'])
    mutable = {'background_job', 'operation_event', 'spring_session', 'spring_session_attributes'}
    changed = [name for name in before['tables'] if before['tables'][name] != final['tables'][name]]
    assert all(name.lower() in mutable for name in changed)
    assert stable_schema(before) == stable_schema(final) and before['flywayHistory'] == final['flywayHistory']
    assert all(final['files'].get(name) == fact for name, fact in before['files'].items())
    for name, fact in final['files'].items():
        path = attempt / 'restored/storage' / name
        assert digest(path) == fact['sha256'] and path.stat().st_size == fact['bytes']
    for name in ('server-private.der', 'server-public.der'):
        assert digest(attempt / 'private' / name) == digest(attempt / 'restored/config' / name)
    return dict(tables=len(before['tables']), changedBeforeAdvance=changed, staleAllFactsInvariant=True,
                sourceInvariant=True, originalFiles=len(before['files']), finalFiles=len(final['files']))


def exports(attempt):
    result = {}
    for extension in ('xlsx', 'pdf'):
        pending = load(attempt / ('pending-' + extension + '.json'))
        prefix = 'restored-stale-' + extension
        record = load(attempt / (prefix + '.json'))
        view, payload = record['view'], record['payload']
        assert record['id'] == view['id'] == pending['id'] and record['key'] == pending['key']
        assert view['status'] == 'SUCCEEDED' and view['attempts'] == 1 and view['downloadable']
        assert {k: v for k, v in payload.items() if k != 'artifact'} == {
            k: v for k, v in pending['payload'].items() if k != 'artifact'}
        snap, file = attempt / (prefix + '.snapshot.json'), attempt / (prefix + '.' + extension)
        assert digest(snap) == payload['snapshotHash']
        assert digest(file) == view['sha256'] == payload['artifact']['sha256']
        assert file.stat().st_size == view['bytes'] == payload['artifact']['bytes']
        parsed = pdf(file, load(snap)) if extension == 'pdf' else xlsx(file, load(snap))
        result[extension] = dict(id=pending['id'], sha256=digest(file), **parsed)
    return result


def pending(attempt):
    record = load(attempt / 'stale-pending.json')
    assert record['view']['status'] == 'PENDING' and record['view']['attempts'] == 0
    assert not record['view']['downloadable'] and record['payload']['artifact'] is None
    table = load(attempt / 'restored-final.json')['tables']['background_job']
    assert len(table['rows']) == 3
    rows = {str(uuid.UUID(bytes=base64.b64decode(row[0]))): dict(zip(table['columns'], row, strict=True))
            for row in table['rows']}
    row = rows[record['id']]
    assert base64.b64decode(row['status']) == b'PENDING' and base64.b64decode(row['attempt_count']) == b'0'
    assert row['lease_owner'] is None and row['lease_token'] is None and row['lease_until'] is None
    return dict(id=record['id'], attempts=0, pendingAfterExplicitOldWorkerCalls=True)


def http(attempt):
    expected = {'pending': [401, 200, 200] + [200, 200] * 2,
                'restored-stale': [401, 200, 200, 404] + [200, 200, 200, 409, 200, 200] * 2,
                'stale': [200, 200, 200, 200, 200] + [503] * 6}
    for phase, statuses in expected.items():
        actual = []
        for path in sorted((attempt / ('private/http-' + phase)).glob('*.http.json')):
            metadata = load(path)
            body = path.with_name(path.name.replace('.http.json', '.body'))
            assert digest(body) == metadata['sha256'] and metadata['status'] == metadata['expected']
            actual.append(metadata['status'])
            if metadata['status'] == 503:
                assert load(body)['code'] == 'CONTROL_INSTANCE_STALE'
        assert actual == statuses, (phase, actual)
    return expected


def processes(attempt):
    home = attempt / 'private/restored-stale'
    pids = []
    for role in ('api', 'worker'):
        ready = load(home / (role + '-ready.json'))
        assert ready['role'] == role and ready['web'] == (role == 'api')
        assert (home / (role + '-closed.marker')).is_file()
        options = load(home / (role + '-options.json'))
        assert any(option.startswith('--spring.datasource.url=') and '_restore?' in option for option in options)
        assert '--outbound.recovery.admission.enabled=true' in options
        assert not any(option.startswith('--outbound.recovery.lease.') for option in options)
        pids.append(ready['pid'])
    assert len(set(pids)) == 2 and (home / 'worker-paused.marker').is_file()
    stale = load(home / 'worker-stale.json')
    assert stale['pid'] == pids[1] and stale['productionPollCalled'] and stale['productionReminderCalled']
    assert stale['admissionCode'] == 'CONTROL_INSTANCE_STALE' and stale['liveRoots'] == 0
    worker_log = (home / 'worker.stdout').read_text(encoding='utf-8', errors='replace')
    assert 'Background worker poll failed (RecoveryControlException)' in worker_log
    assert 'Background reminder scheduling failed (RecoveryControlException)' in worker_log
    return dict(pids=pids, distinct=True, normalShutdown=True, staleWorker=stale, p5=False)


def control(attempt):
    home = attempt / 'private/stale-authority'
    assert load(home / 'setup-exit.json')['exitCode'] == load(home / 'advance-exit.json')['exitCode'] == 0
    assert (home / 'setup.stdout').read_text().strip() == 'CREATED'
    result = load(home / 'advance.stdout')
    assert result['record']['revision'] == '2' and not result['ordinaryWritesAllowed']
    fixture = load(home / 'control/fixture.json')
    authority = Path(fixture['root'])
    assert authority.is_relative_to(home)
    history = sorted((authority / 'history').glob('*.json'))
    assert [load(path)['revision'] for path in history] == ['1', '2']
    assert load(authority / 'current.json')['recordSha256'] == result['record']['recordSha256']
    return dict(revision='2', ordinaryWritesAllowed=False, independentPython=True,
                records=[dict(path=str(path.relative_to(attempt)), sha256=digest(path)) for path in history])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--attempt', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args(); attempt = args.attempt.resolve()
    assert (attempt / 'test-completed.marker').is_file()
    assert load(attempt / 'mysql-stopped.json')['exitCode'] == 0
    result = dict(conservation=conservation(attempt), exports=exports(attempt), pending=pending(attempt),
                  http=http(attempt), processes=processes(attempt), control=control(attempt),
                  dumpSHA256=digest(attempt / 'private/final-dump.stdout'))
    save(args.output, result)
    print(json.dumps(dict(status='PASS', output=str(args.output))))


if __name__ == '__main__':
    main()
