"""Independently consume retained restored failures, HTTP retries and actual files."""
import argparse
import base64
import json
from pathlib import Path
import uuid

from d13_export_file_consume import pdf, xlsx
from d15_combination_runtime import digest, save
from verify_d13_restored_scheduler import processes, stable_schema


def load(path):
    return json.loads(path.read_bytes())


def conservation(attempt):
    before = load(attempt / 'restored-before.json')
    failed, final = [load(attempt / name) for name in ('restored-failed.json', 'restored-final.json')]
    assert before == load(attempt / 'source-final.json') == load(attempt / 'private/archive-snapshot-facts.json')
    mutable = {'background_job', 'operation_event', 'spring_session', 'spring_session_attributes'}
    changes = []
    for after in (failed, final):
        assert set(before['tables']) == set(after['tables'])
        changed = [name for name in before['tables'] if before['tables'][name] != after['tables'][name]]
        assert all(name.lower() in mutable for name in changed)
        assert stable_schema(before) == stable_schema(after) and before['flywayHistory'] == after['flywayHistory']
        assert all(after['files'].get(name) == fact for name, fact in before['files'].items())
        changes.append(changed)
    assert all(final['files'].get(name) == fact for name, fact in failed['files'].items())
    for name, fact in final['files'].items():
        path = attempt / 'restored/storage' / name
        assert digest(path) == fact['sha256'] and path.stat().st_size == fact['bytes']
    for name in ('server-private.der', 'server-public.der'):
        assert digest(attempt / 'private' / name) == digest(attempt / 'restored/config' / name)
    return dict(tables=len(before['tables']), changedTables=changes, sourceInvariant=True,
                oldFiles=len(before['files']), failedFiles=len(failed['files']), finalFiles=len(final['files']))


def job_rows(facts):
    table = facts['tables']['background_job']
    return {str(uuid.UUID(bytes=base64.b64decode(row[0]))): dict(zip(table['columns'], row, strict=True))
            for row in table['rows']}


def original_jobs(attempt):
    failed = job_rows(load(attempt / 'restored-failed.json'))
    final = job_rows(load(attempt / 'restored-final.json'))
    assert len(failed) == 2 and len(final) == 4
    assert all(final.get(key) == row for key, row in failed.items())
    result = {}
    for extension in ('xlsx', 'pdf'):
        pending, failure = [load(attempt / (phase + '-' + extension + '.json')) for phase in ('pending', 'failed')]
        assert pending['id'] == failure['id'] and pending['key'] == failure['key']
        assert failure['payload'] == pending['payload'] and pending['payload']['artifact'] is None
        view = failure['view']
        assert view['status'] == 'FAILED' and view['attempts'] == 3 and not view['downloadable']
        assert view['errorCode'] == 'EXPORT_RENDER_FAILED' and not view['sha256']
        row = failed[pending['id']]
        assert base64.b64decode(row['status']) == b'FAILED' and base64.b64decode(row['attempt_count']) == b'3'
        assert row['lease_owner'] is None and row['lease_token'] is None and row['lease_until'] is None
        result[extension] = dict(id=pending['id'], attempts=3, failedRowUnchanged=True)
    return result


def exports(attempt):
    results = {}
    for extension in ('xlsx', 'pdf'):
        pending = load(attempt / ('pending-' + extension + '.json'))
        record = load(attempt / ('retry-' + extension + '.json'))
        view, payload = record['retryView'], record['retryPayload']
        assert record['id'] == pending['id'] and record['retryId'] != pending['id']
        assert view['id'] == record['retryId'] and view['status'] == 'SUCCEEDED' and view['attempts'] == 1
        assert view['retryOf'] == payload['retryOf'] == pending['id'] and view['downloadable']
        assert {k: v for k, v in payload.items() if k not in ('artifact', 'expiresAt', 'retryOf')} == {
            k: v for k, v in pending['payload'].items() if k not in ('artifact', 'expiresAt', 'retryOf')}
        snap = attempt / ('retry-' + extension + '.snapshot.json')
        path = attempt / ('retry-' + extension + '.' + extension)
        assert digest(snap) == payload['snapshotHash'] == pending['payload']['snapshotHash']
        assert digest(path) == payload['artifact']['sha256'] == view['sha256']
        assert path.stat().st_size == payload['artifact']['bytes'] == view['bytes']
        parsed = pdf(path, load(snap)) if extension == 'pdf' else xlsx(path, load(snap))
        results[extension] = dict(id=record['retryId'], original=pending['id'], sha256=digest(path), **parsed)
    return results


def http(attempt):
    expected = {'pending': [401, 200, 200] + [200, 200] * 2,
                'failed': [401, 200, 200] + [200, 409, 200, 200] * 2,
                'successor': [401, 200, 200, 200, 404] + [200, 200, 200, 200, 200, 409, 200, 200] * 2}
    for phase, statuses in expected.items():
        actual = []
        for path in sorted((attempt / ('private/http-' + phase)).glob('*.http.json')):
            record = load(path)
            assert record['expected'] == record['status']
            assert digest(path.with_name(path.name.replace('.http.json', '.body'))) == record['sha256']
            actual.append(record['status'])
        assert actual == statuses, (phase, actual)
    return expected


def renderer_failure(attempt):
    home = attempt / 'private/restored-split'
    before, after = [load(home / name) for name in ('worker-options-before-injection.json', 'worker-options.json')]
    differences = [(a, b) for a, b in zip(before, after, strict=True) if a != b]
    assert len(differences) == 1 and differences[0][0].startswith('--outbound.exports.renderer-home=')
    assert differences[0][1] == '--outbound.exports.renderer-home=' + str(attempt / 'broken-renderer')
    assert not list((attempt / 'broken-renderer/lib').iterdir())
    logs = []
    for extension in ('xlsx', 'pdf'):
        record = load(attempt / ('pending-' + extension + '.json'))
        paths = sorted((attempt / 'restored/storage/exports').rglob('renderer.log'))
        selected = [path for path in paths if record['id'] in str(path)]
        assert len(selected) == 3, (extension, selected)
        for path in selected:
            content = path.read_text(encoding='utf-8', errors='replace')
            assert 'ClassNotFoundException: com.acrylic.outbound.reports.ReportCli' in content
            logs.append(dict(path=str(path.relative_to(attempt)), sha256=digest(path)))
    return dict(injection='worker-only empty disposable renderer lib', failures=logs)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--attempt', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    attempt = args.attempt.resolve()
    assert (attempt / 'test-completed.marker').is_file()
    assert load(attempt / 'mysql-stopped.json')['exitCode'] == 0
    result = dict(conservation=conservation(attempt), originals=original_jobs(attempt), exports=exports(attempt),
                  http=http(attempt), processes=processes(attempt), rendererFailure=renderer_failure(attempt))
    result['dumpSHA256'] = digest(attempt / 'private/final-dump.stdout')
    save(args.output, result)
    print(json.dumps(dict(status='PASS', output=str(args.output))))


if __name__ == '__main__':
    main()
