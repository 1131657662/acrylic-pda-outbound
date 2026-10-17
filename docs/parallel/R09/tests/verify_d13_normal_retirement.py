"""Independent offline consumption of synthetic normal/failed retirement process artifacts."""
import argparse
import hashlib
import hmac
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

from verify_d13_admitted_worker import artifact, digest, load
from verify_d13_renderer_handoff import fence, preview


def tagged(path, secret, field='anchorTag'):
    value = load(path)
    body = dict(value)
    tag = body.pop(field)
    raw = (json.dumps(body, ensure_ascii=False, sort_keys=True, separators=(',', ':')) + '\n').encode()
    assert hmac.compare_digest(tag, hmac.new(secret, raw, hashlib.sha256).hexdigest())
    return value


def normal(home):
    config = load(home / 'config.json')
    secret = bytes.fromhex(config['anchor'])
    original = tagged(home / 'published-original.json', secret)
    index = tagged(home / 'p5/leases/index.json', secret, 'indexTag')
    assert index['entries'] == []
    assert list((home / 'p5/leases').iterdir()) == [home / 'p5/leases/index.json']
    assert (home / 'normal-return.txt').read_text() == 'RELEASED|roots=0'
    assert 'STARTED' in (home / 'next.stdout').read_text('utf-8')
    owner = home / 'p5/processes' / (original['processIdentity'] + '.json')
    tagged(owner, secret)
    assert digest(owner) == original['processSha256']
    return dict(roots=0, leaseEntries=0, originalProcessPreserved=True, successorStarted=True,
                originalSha256=digest(home / 'published-original.json'), indexSha256=digest(home / 'p5/leases/index.json'))


def refused(home):
    secret = bytes.fromhex(load(home / 'config.json')['anchor'])
    original = tagged(home / 'published-original.json', secret)
    assert digest(home / 'published-original.json') == digest(home / 'orphan-original.json')
    unknown = load(home / 'unknown-original.json')
    assert unknown['operationId'] == original['operationId']
    assert unknown['processIdentity'] == original['processIdentity']
    assert unknown['event'] == 'RECOVERY_LEASE_FENCED'
    assert not (home / 'normal-return.txt').exists()
    assert 'REAL Windows handle denies FILE_SHARE_DELETE' in (home / 'deny-delete-handle.txt').read_text()
    assert 'RECOVERY_LEASE_OUTSTANDING' in (home / 'next.stdout').read_text('utf-8')
    stderr = (home / 'owner.stdout.stderr').read_text('utf-8')
    for event in ('FENCE_F1_PRIME_CONFIRMED', 'FENCE_F2_AUDIT_CONFIRMED',
                  'FENCE_F3_RELEASE_CONFIRMED', 'FENCE_F4_WRITERS_RETAINED'):
        assert event in stderr
    return dict(nativeDenyDelete=True, normalReturn=False, successorRefused=True,
                originalSha256=digest(home / 'orphan-original.json'), unknownSha256=digest(home / 'unknown-original.json'))


def suites(attempt):
    result = load(attempt / 'test-result.json')
    assert result['exitCode'] == 0
    required = {'RecoveryLeaseRetirementTest', 'RecoveryOperationRetirementTest', 'RecoveryLeasePublicationTest',
                'RecoveryWriterLeaseTest', 'RecoveryWriterReferenceConcurrencyTest', 'RecoveryRequestAdmittedDataSourceTest',
                'RecoveryProcessHandoffTest', 'ExportRendererProcessTest', 'RecoveryEffectSqlTest', 'RecoverySelectSqlTest',
                'CommandScalarRowsTest', 'RecoveryRetirementNativeIT', 'RecoveryRendererFenceIT'}
    if result['productHttp']:
        required.add('E2aAdmittedWorkerExportIT')
    assert {row['name'].rsplit('.', 1)[-1] for row in result['suites']} == required
    counts = dict(test=0, integrationTest=0)
    for row in result['suites']:
        source = attempt / ('TEST-' + row['name'] + '.xml')
        suite = ET.parse(source).getroot().attrib
        assert all(suite[key] == row[key] for key in ('tests', 'failures', 'errors', 'skipped'))
        assert int(suite['tests']) > 0 and suite['failures'] == suite['errors'] == suite['skipped'] == '0'
        counts[row['task']] += int(suite['tests'])
    assert counts['integrationTest'] == (4 if result['productHttp'] else 3)
    return counts


def product(attempt):
    assert load(attempt / 'mysql-stopped.json')['listener'] is False
    assert (attempt / 'test-completed.marker').exists()
    prefixes = [('default-off-xlsx', 'xlsx'), ('admitted-xlsx', 'xlsx'), ('admitted-pdf', 'pdf'),
                ('p5-xlsx', 'xlsx'), ('p5-pdf', 'pdf')]
    artifacts = {name: artifact(attempt, name, extension) for name, extension in prefixes}
    orders = [load(attempt / (name + '.snapshot.json'))['orders'] for name, _ in prefixes]
    assert all(value == orders[0] for value in orders)
    checks = load(attempt / 'worker-checks.json')
    assert checks['kind'] == 'WORKER_JOB'
    assert all(value is True for key, value in checks.items() if key not in ('kind', 'probeJob'))
    control = attempt / 'worker-authority'
    assert (control / 'python-call-1.out').read_text().strip() == 'CONTROL_WRITERS_ACTIVE'
    advanced = load(control / 'python-call-2.out')
    assert advanced['status'] == 'PASS' and advanced['record']['revision'] == '2'
    assert advanced['ordinaryWritesAllowed'] is False and advanced['localDeletionAllowed'] is False
    assert load(attempt / 'private/http-stale/001.http.json')['status'] == 503
    assert load(attempt / 'private/http-stale/001.body')['code'] == 'CONTROL_INSTANCE_STALE'
    sql = (attempt / 'private/final-dump.stdout').read_bytes()
    assert sql.count(b"'REPORT_RENDER_V1'") == 5
    assert b'INSERT INTO `recovery_worker_probe`' not in sql
    assert b'INSERT INTO `recovery_normal_admission`' not in sql
    assert re.search(rb"\(0x[0-9A-F]+,'RECOVERY_WORKER_PROBE','[0-9a-f-]+','FAILED',", sql)
    return dict(artifacts=artifacts, ordersEqual=True, worker=checks, dumpSha256=digest(attempt / 'private/final-dump.stdout'),
                p5=application_p5(attempt))


def application_p5(attempt):
    home = attempt / 'private/p5-retirement'
    secret = bytes.fromhex(load(home / 'config.json')['anchor'])
    index = tagged(home / 'p5/leases/index.json', secret, 'indexTag')
    drained = tagged(home / 'drained-index.json', secret, 'indexTag')
    assert index == drained and index['entries'] == []
    assert list((home / 'p5/leases').iterdir()) == [home / 'p5/leases/index.json']
    processes = sorted((home / 'p5/processes').glob('*.json'))
    # The drain records two app contexts; the successful independent successor adds its own record.
    assert len(processes) == 3
    for path in processes:
        tagged(path, secret)
    assert 'STARTED' in (home / 'successor.stdout').read_text('utf-8')
    assert (home / 'confirmed.txt').exists()
    return dict(indexSha256=digest(home / 'p5/leases/index.json'), processHashes=[digest(p) for p in processes],
                successorStarted=True, normalEntries=0)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--attempt', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    assert not args.output.exists()
    args.output.parent.mkdir(parents=True, exist_ok=False)
    home = args.attempt.resolve()
    http = load(home / 'test-result.json')['productHttp']
    summary = dict(result='PASS', scope='bounded server L1 retirement; not D13 overall, L2 or L3',
                   suites=suites(home), normal=normal(home / 'private/retirement-normal'),
                   refused=refused(home / 'private/retirement-delete-failure'), renderer=fence(home))
    if http:
        summary['product'] = product(home)
        summary['previews'] = preview(home, args.output.parent)
    with args.output.open('x', encoding='utf-8') as output:
        json.dump(summary, output, indent=2)
    print(json.dumps(summary))


if __name__ == '__main__':
    main()
