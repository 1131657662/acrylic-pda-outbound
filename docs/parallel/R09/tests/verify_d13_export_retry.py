"""Independently consume actual retry HTTP, fixed files, SQL and retained P5 records."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

from d13_export_file_consume import pdf, xlsx
from verify_d13_admitted_worker import digest, load
from verify_d13_normal_retirement import application_p5


def exported(attempt, prefix):
    record = load(attempt / (prefix + '.json'))
    view, payload = record['view'], record['payload']
    extension = 'pdf' if payload['format'] == 'PDF' else 'xlsx'
    path, snapshot = attempt / (prefix + '.' + extension), attempt / (prefix + '.snapshot.json')
    assert digest(path) == view['sha256'] == payload['artifact']['sha256']
    assert path.stat().st_size == view['bytes'] == payload['artifact']['bytes']
    assert digest(snapshot) == payload['snapshotHash']
    assert view['status'] == 'SUCCEEDED' and view['downloadable'] and view['attempts'] == 1
    result = pdf(path, load(snapshot)) if extension == 'pdf' else xlsx(path, load(snapshot))
    return dict(**result, id=view['id'], retryOf=payload['retryOf'],
                sha256=digest(path), snapshotSha256=digest(snapshot))


def http(attempt):
    results = {}
    for directory in sorted((attempt / 'private').glob('http-retry-*')):
        metadata = sorted(directory.glob('*.http.json'))
        statuses = []
        for index, path in enumerate(metadata, 1):
            assert path.name == f'{index:03d}.http.json'
            row = load(path)
            assert row['status'] == row['expected']
            assert digest(path.with_name(path.name.replace('.http.json', '.body'))) == row['sha256']
            statuses.append(row['status'])
        results[directory.name] = dict(requests=len(metadata), statuses=statuses)
    for mode in ('off', 'p5'):
        statuses = results['http-retry-' + mode]['statuses']
        assert statuses[0] == 401 and 404 in statuses and 409 in statuses
    assert results['http-retry-revoked']['statuses'][-1] == 401
    rollback = load(attempt / 'retry-rollback.json')
    failures = [load(path) for path in (attempt / 'private/http-retry-p5').glob('*.body')
                if path.read_bytes().startswith(b'{')]
    assert rollback['failureCode'] == 'WRITE_CONFLICT'
    assert any(row.get('requestId') == rollback['failureRequestId'] and
               row.get('code') == 'WRITE_CONFLICT' for row in failures)
    return results


def suites(attempt):
    record = load(attempt / 'test-result.json')
    assert record['exitCode'] == 0
    counts = dict(test=0, integrationTest=0)
    for row in record['suites']:
        xml = ET.parse(attempt / ('TEST-' + row['name'] + '.xml')).getroot().attrib
        assert all(xml[key] == row[key] for key in ('tests', 'failures', 'errors', 'skipped'))
        assert xml['failures'] == xml['errors'] == xml['skipped'] == '0' and int(xml['tests']) > 0
        counts[row['task']] += int(xml['tests'])
    assert counts['integrationTest'] == 1
    return counts


def product(attempt):
    prefixes = [mode + '-' + kind + '-' + format for mode in ('off', 'p5')
                for format in ('xlsx', 'pdf') for kind in ('original', 'retry')]
    prefixes += ['p5-concurrent', 'p5-tail-recovered']
    artifacts = {prefix: exported(attempt, prefix) for prefix in prefixes}
    for mode in ('off', 'p5'):
        for format in ('xlsx', 'pdf'):
            original, retry = (artifacts[f'{mode}-{kind}-{format}'] for kind in ('original', 'retry'))
            assert retry['retryOf'] == original['id'] and retry['id'] != original['id']
            assert retry['snapshotSha256'] == original['snapshotSha256']
    rollback, revoked = load(attempt / 'retry-rollback.json'), load(attempt / 'retry-revoked.json')
    assert rollback['rolledBack'] and revoked['lockWaitObserved'] and revoked['noNewJob']
    assert rollback['jobsAfterFailure'] == 0 and rollback['eventsBeforeFailure'] == rollback['eventsAfterFailure']
    assert rollback['recoveredId'] == artifacts['p5-tail-recovered']['id']
    concurrent = load(attempt / 'retry-concurrent.json')
    assert concurrent['jobs'] == 1 and concurrent['bothId'] == artifacts['p5-concurrent']['id']
    sql = (attempt / 'private/final-dump.stdout').read_bytes()
    assert sql.count(b"'REPORT_RENDER_V1'") == len(prefixes)
    assert revoked['key'].encode() not in sql
    assert b'INSERT INTO `recovery_normal_admission`' not in sql
    return dict(artifacts=artifacts, concurrent=concurrent, rollback=rollback, revoked=revoked,
                dumpSha256=digest(attempt / 'private/final-dump.stdout'), p5=application_p5(attempt))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--attempt', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    assert not args.output.exists()
    stopped = load(args.attempt / 'mysql-stopped.json')
    assert stopped['exitCode'] == 0 and stopped['listener'] is False
    assert (args.attempt / 'test-completed.marker').exists()
    summary = dict(result='PASS', scope='bounded server retry L1; not overall D13/L2/L3',
                   suites=suites(args.attempt), http=http(args.attempt), product=product(args.attempt))
    args.output.parent.mkdir(parents=True, exist_ok=False)
    args.output.write_text(json.dumps(summary, indent=2), encoding='utf-8')
    print(json.dumps(summary))


if __name__ == '__main__':
    main()
