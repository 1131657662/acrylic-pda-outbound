"""Read retained HTTP, SQL snapshots, P5 and actual XLSX/PDF without starting services."""
import argparse
import json
import re

from d13_export_file_consume import pdf, xlsx
from d15_combination_runtime import digest, save


def load(path):
    return json.loads(path.read_bytes())


def stable_schema(facts):
    return {name: re.sub(' AUTO_INCREMENT=[0-9]+', '', ddl) if name.lower() == 'operation_event' else ddl
            for name, ddl in facts['schema'].items()}


def conservation(attempt):
    before = load(attempt / 'restored-before.json')
    first, final = [load(attempt / name) for name in ('restored-first.json', 'restored-final.json')]
    assert before == load(attempt / 'source-final.json') == load(attempt / 'private/archive-snapshot-facts.json')
    mutable = {'background_job', 'operation_event', 'spring_session', 'spring_session_attributes'}
    changed = []
    for after in (first, final):
        assert set(before['tables']) == set(after['tables'])
        delta = [name for name in before['tables'] if before['tables'][name] != after['tables'][name]]
        assert all(name.lower() in mutable for name in delta)
        assert stable_schema(before) == stable_schema(after) and before['flywayHistory'] == after['flywayHistory']
        assert all(after['files'].get(name) == value for name, value in before['files'].items())
        changed.append(delta)
    assert first['tables']['background_job'] == final['tables']['background_job']
    assert first['files'] == final['files']
    for name, fact in final['files'].items():
        path = attempt / 'restored/storage' / name
        assert digest(path) == fact['sha256'] and path.stat().st_size == fact['bytes']
    for name in ('server-private.der', 'server-public.der'):
        assert digest(attempt / 'private' / name) == digest(attempt / 'restored/config' / name)
    return dict(tables=len(before['tables']), changedTables=changed, sourceInvariant=True,
                oldFiles=len(before['files']), finalFiles=len(final['files']), signingMaterialIdentical=True)


def export(attempt, extension):
    pending = load(attempt / ('pending-' + extension + '.json'))
    results = []
    for phase in ('restored-split', 'restored-split-successor'):
        prefix = phase + '-' + extension
        record = load(attempt / (prefix + '.json'))
        path, snap = attempt / (prefix + '.' + extension), attempt / (prefix + '.snapshot.json')
        view, payload = record['view'], record['payload']
        assert record['id'] == pending['id'] == view['id'] and record['key'] == pending['key']
        assert view['status'] == 'SUCCEEDED' and view['attempts'] == 1 and view['downloadable']
        assert {k: v for k, v in payload.items() if k != 'artifact'} == {
            k: v for k, v in pending['payload'].items() if k != 'artifact'}
        assert digest(snap) == payload['snapshotHash']
        assert digest(path) == view['sha256'] == payload['artifact']['sha256']
        assert path.stat().st_size == view['bytes'] == payload['artifact']['bytes']
        parsed = pdf(path, load(snap)) if extension == 'pdf' else xlsx(path, load(snap))
        results.append(dict(phase=phase, sha256=digest(path), snapshotSHA=digest(snap), **parsed))
    assert results[0]['sha256'] == results[1]['sha256']
    return dict(jobId=pending['id'], attempts=1, consumers=results)


def http(attempt):
    result = {}
    expected = {'pending': [401, 200, 200, 200, 200, 200, 200]}
    for phase in ('restored-split', 'restored-split-successor'):
        expected[phase] = [401, 200, 200, 404] + [200, 200, 200, 409, 200, 200] * 2
    for phase, statuses in expected.items():
        actual = []
        for path in sorted((attempt / ('private/http-' + phase)).glob('*.http.json')):
            metadata = load(path)
            assert metadata['expected'] == metadata['status']
            assert digest(path.with_name(path.name.replace('.http.json', '.body'))) == metadata['sha256']
            actual.append(metadata['status'])
        assert actual == statuses, (phase, actual)
        result[phase] = actual
    return result


def processes(attempt):
    pids = []
    for phase in ('restored-split', 'restored-split-successor'):
        home = attempt / 'private' / phase
        for role in ('api', 'worker'):
            ready = load(home / (role + '-ready.json'))
            assert ready['role'] == role and ready['web'] == (role == 'api')
            assert (home / (role + '-closed.marker')).is_file()
            options = load(home / (role + '-options.json'))
            assert any(option.startswith('--spring.datasource.url=') and '_restore?' in option for option in options)
            pids.append(ready['pid'])
    assert len(set(pids)) == 4
    home = attempt / 'private/p5-retirement/p5'
    assert not load(home / 'leases/index.json')['entries']
    assert [path.name for path in (home / 'leases').iterdir()] == ['index.json']
    assert {load(path)['pid'] for path in (home / 'processes').glob('*.json')} == set(pids)
    for count in (2, 4):
        assert not load(attempt / ('retired-' + str(count) + '.json'))['entries']
    return dict(pids=pids, distinct=True, retired=True, successorFullApiWorker=True)


def main():
    from pathlib import Path
    parser = argparse.ArgumentParser()
    parser.add_argument('--attempt', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    attempt = args.attempt.resolve()
    assert (attempt / 'test-completed.marker').is_file()
    assert load(attempt / 'mysql-stopped.json')['exitCode'] == 0
    result = dict(conservation=conservation(attempt), http=http(attempt), processes=processes(attempt))
    result.update({extension: export(attempt, extension) for extension in ('xlsx', 'pdf')})
    result['dumpSHA256'] = digest(attempt / 'private/final-dump.stdout')
    save(args.output, result)
    print(json.dumps(dict(status='PASS', output=str(args.output))))


if __name__ == '__main__':
    main()
