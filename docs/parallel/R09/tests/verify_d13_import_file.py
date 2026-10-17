"""Offline consumer of actual import HTTP, native lock probes and signed P5 files."""
import argparse
import hashlib
import hmac
import json
from pathlib import Path
import xml.etree.ElementTree as ET

from d13_import_file_consume import digest, load, product


def tagged(path, secret, field='anchorTag'):
    value = load(path)
    body = dict(value)
    tag = body.pop(field)
    raw = (json.dumps(body, ensure_ascii=False, sort_keys=True, separators=(',', ':')) + '\n').encode()
    assert hmac.compare_digest(tag, hmac.new(secret, raw, hashlib.sha256).hexdigest())
    return value


def http(attempt):
    results = {}
    for directory in sorted((attempt / 'private').glob('http-import-*')):
        statuses, codes = [], []
        for index, path in enumerate(sorted(directory.glob('*.http.json')), 1):
            assert path.name == f'{index:03d}.http.json'
            row = load(path)
            body = path.with_name(path.name.replace('.http.json', '.body'))
            assert row['status'] == row['expected'] and digest(body) == row['sha256']
            statuses.append(row['status'])
            codes.append(load(body)['code'])
        results[directory.name] = dict(requests=len(statuses), statuses=statuses, codes=codes)
    for mode in ('off', 'p5'):
        result = results['http-import-' + mode]
        assert result['statuses'][0] == 401 and 404 in result['statuses'] and 409 in result['statuses']
        assert 'IDEMPOTENCY_CONTENT_MISMATCH' in result['codes']
        assert results['http-import-' + mode + '-viewer']['statuses'][-1] == 403
    assert 'WRITE_CONFLICT' in results['http-import-p5']['codes']
    assert results['http-import-p5-revoked']['codes'][-1] == 'SESSION_REVOKED'
    return results


def p5(attempt):
    home = attempt / 'private/p5-retirement'
    secret = bytes.fromhex(load(home / 'config.json')['anchor'])
    held = tagged(attempt / 'import-held-index.json', secret, 'indexTag')
    drained = tagged(home / 'drained-index.json', secret, 'indexTag')
    final = tagged(home / 'p5/leases/index.json', secret, 'indexTag')
    assert held['entries'] and drained == final and not final['entries']
    assert held['installationId'] == final['installationId']
    assert list((home / 'p5/leases').iterdir()) == [home / 'p5/leases/index.json']
    processes = sorted((home / 'p5/processes').glob('*.json'))
    assert len(processes) == 2
    for path in processes:
        tagged(path, secret)
    assert 'STARTED' in (home / 'successor.stdout').read_text('utf-8')
    probes = [load(attempt / ('import-writer-' + name + '.stdout')) for name in ('held', 'after-drain')]
    assert [row['status'] for row in probes] == ['BLOCKED', 'FREE']
    assert probes[0]['path'] == probes[1]['path'] and probes[0]['sha256'] == probes[1]['sha256']
    assert all(row['nonmutating'] for row in probes)
    return dict(heldRootCount=len(held['entries']), drainedRoots=0, writerProbes=probes,
                processSha256=[digest(p) for p in processes], successorStarted=True)


def suites(attempt):
    result = load(attempt / 'test-result.json')
    assert result['exitCode'] == 0 and result['missing'] == []
    counts = dict(test=0, integrationTest=0)
    for row in result['suites']:
        xml = ET.parse(attempt / ('TEST-' + row['name'] + '.xml')).getroot().attrib
        assert all(xml[key] == row[key] for key in ('tests', 'failures', 'errors', 'skipped'))
        assert int(xml['tests']) > 0 and xml['failures'] == xml['errors'] == xml['skipped'] == '0'
        counts[row['task']] += int(xml['tests'])
    assert counts['integrationTest'] == 1
    return counts


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--attempt', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    assert not args.output.exists()
    stopped = load(args.attempt / 'mysql-stopped.json')
    assert stopped['exitCode'] == 0 and stopped['listener'] is False
    assert (args.attempt / 'test-completed.marker').exists()
    result = dict(result='PASS', scope='bounded server import-file L1; not D13 overall/L2/L3',
                  suites=suites(args.attempt), http=http(args.attempt), product=product(args.attempt),
                  p5=p5(args.attempt))
    args.output.parent.mkdir(parents=True, exist_ok=False)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
