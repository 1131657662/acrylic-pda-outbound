"""Independent original-byte consumer; no application observer or decision API."""
import argparse
import hashlib
import hmac
import json
from pathlib import Path
import re


def canonical(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')) + '\n').encode('utf-8')


def read(path):
    raw = path.read_bytes()
    value = json.loads(raw)
    assert raw == canonical(value), f'Noncanonical original: {path}'
    return value


def authenticated(path, anchor, tag='anchorTag'):
    value = read(path)
    body = {k: v for k, v in value.items() if k != tag}
    actual = hmac.new(anchor, canonical(body), hashlib.sha256).hexdigest()
    assert hmac.compare_digest(value[tag], actual), f'Invalid original tag: {path}'
    return value


def original_chain(output):
    configs = list(output.glob('fence-*/trusted-config.json'))
    assert len(configs) == 1
    config = read(configs[0])
    base = configs[0].parent
    originals = base / 'originals-before-observation'
    anchor = bytes.fromhex(config['anchor'])
    assert len(anchor) == 32
    unknowns = list(originals.glob('leases/*.unknown.json'))
    assert len(unknowns) == 1
    unknown = read(unknowns[0])  # UNKNOWN is an unsigned audit, not an anchored lease.
    assert set(unknown) == {'operationId', 'processIdentity', 'event', 'at'}
    operation, process = unknown['operationId'], unknown['processIdentity']
    lease = authenticated(originals / f'leases/{operation}.json', anchor)
    owner = authenticated(originals / f'processes/{process}.json', anchor)
    index = authenticated(originals / 'leases/index.json', anchor, 'indexTag')
    assert index['entries'] == [operation] and unknown['event'] == 'RECOVERY_LEASE_FENCED'
    assert lease['operationId'] == operation and lease['processIdentity'] == process == owner['processIdentity']
    assert lease['processSha256'] == hashlib.sha256(canonical(owner)).hexdigest()
    assert owner['identitySha256'] == config['identitySha256']
    assert lease['installationId'] == owner['installationId'] == index['installationId'] == config['installationId']
    assert (base / f'p5/leases/{operation}.unknown.json').read_bytes() == unknowns[0].read_bytes()
    assert not (base / f'p5/leases/{operation}.json').exists()  # Orphan scan retired the lease only.
    assert (base / f'p5/processes/{process}.json').read_bytes() == (originals / f'processes/{process}.json').read_bytes()
    assert authenticated(base / 'p5/leases/index.json', anchor, 'indexTag')['entries'] == []
    return base, operation, owner


def children(base, operation, owner):
    paths = list(base.glob('child-*'))
    assert len(paths) == 2
    lost = [p for p in paths if (p / 'operation.txt').exists()]
    assert len(lost) == 1
    lost = lost[0]
    fresh = next(p for p in paths if p != lost)
    assert (lost / 'operation.txt').read_text('utf-8') == operation
    receipt = (lost / 'receipt.txt').read_text('utf-8')
    assert f'|pid={owner["pid"]}|' in receipt and receipt.endswith('|exit=70')
    assert 'PREPARED|uncommitted=2|delegateInsert=1' in (lost / 'status.txt').read_text('utf-8')
    assert not (lost / 'commit-returned.marker').exists()
    stderr = (lost / 'stderr.txt').read_text('utf-8')
    for token in ('FENCE_F1_PRIME_CONFIRMED', 'FENCE_F2_AUDIT_CONFIRMED', 'FENCE_F3_RELEASE_CONFIRMED', 'FENCE_F4_WRITERS_RETAINED'):
        assert token in stderr
    assert (fresh / 'receipt.txt').read_text('utf-8').endswith('|exit=1')
    assert 'RECOVERY_LEASE_OUTSTANDING' in (fresh / 'stderr.txt').read_text('utf-8')
    assert 'EFFECT|' not in (fresh / 'status.txt').read_text('utf-8')
    return dict(lostCommitPid=owner['pid'], lostCommitExit=70, freshExit=1)


def database(output):
    lines = (output / 'lost-commit-summary.tsv').read_text('utf-8').splitlines()
    assert len(lines) == 2
    table, *values = lines[1].split('\t')
    assert re.fullmatch(r'f_[0-9a-f]{32}', table) and values == ['1', '1', '2', '70', '0', '1']
    assert (output / 'sql-rows.stdout').read_text('utf-8').strip() == '1\tvalue\n2\tvalue'
    allocation = json.loads((output / 'allocation.json').read_text('utf-8'))
    target = f'### INSERT INTO `{allocation["schema"]}`.`{table}`'.encode('ascii')
    binlog = (output / 'binlog.stdout').read_bytes()  # Binary row fields need not be UTF-8.
    sections = binlog.split(target)
    assert len(sections) == 3  # Exactly two durable row inserts, no rejected id99 delegation.
    for section, row in zip(sections[1:], (1, 2)):
        assert re.search(rf'###   @1={row}\b'.encode('ascii'), section.split(b'# at ')[0])
    assert f'### UPDATE `{allocation["schema"]}`.`{table}`'.encode('ascii') not in binlog
    assert f'### DELETE FROM `{allocation["schema"]}`.`{table}`'.encode('ascii') not in binlog
    stopped = json.loads((output / 'mysql-stopped.json').read_text('utf-8'))
    assert stopped['port'] == allocation['port'] and stopped['exitCode'] == 0 and stopped['listener'] is False
    assert (output / 'identity-before.stdout').read_bytes() == (output / 'identity-final.stdout').read_bytes()
    return dict(table=table, rows=2, binlogInsertRows=2, shutdownExit=0)


def observations(output):
    records = (output / 'real-records.tsv').read_text('utf-8')
    for text in ('REAL|normalCommit|before=0|observer=1|driverCommits=1',
                 'REAL|prepared|observer=1|ownerLocked=true|gate=accepted',
                 'REAL|lostCommit|observer=2|ownerLocked=false|exit=70',
                 'REAL|sameJvmNewAuthority|code=RECOVERY_LEASE_FENCED'):
        assert text in records
    for code in ('RECOVERY_LEASE_OUTSTANDING', 'RECOVERY_LEASE_FENCED'):
        assert re.search(r'REAL\|rejection\|code=' + code + r'\|[^\n]+\|delegateDelta=0\|observerDelta=0', records)
    result = json.loads((output / 'test-result.json').read_text('utf-8'))
    assert result['exitCode'] == 0 and [result[k] for k in ('tests', 'failures', 'errors', 'skipped')] == ['1', '0', '0', '0']
    before = json.loads((output / 'source-before.json').read_text('utf-8'))
    assert before == json.loads((output / 'source-after.json').read_text('utf-8'))
    return dict(sourcePaths=len(before), tests=1, failures=0, errors=0, skipped=0)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    assert output.is_relative_to(Path('D:/acrylic-pda-outbound/.local'))
    base, operation, owner = original_chain(output)
    result = dict(status='PASS', scope='MODEL installation/admission; real P5/JDBC/MySQL/OS halt; no full restore/application admission',
                  unknownAuditAuthenticated=False, anchoredRecords=['lease', 'process', 'index'], operation=operation)
    result.update(children(base, operation, owner))
    result.update(database(output))
    result.update(observations(output))
    manifest = {p.relative_to(output).as_posix(): dict(bytes=p.stat().st_size, sha256=hashlib.sha256(p.read_bytes()).hexdigest())
                for p in sorted(output.rglob('*')) if p.is_file()}
    result['evidence'] = manifest
    with (output / 'independent-verification.json').open('x', encoding='utf-8') as stream:
        json.dump(result, stream, indent=2)
    print(json.dumps({k: v for k, v in result.items() if k != 'evidence'}))


if __name__ == '__main__':
    main()
