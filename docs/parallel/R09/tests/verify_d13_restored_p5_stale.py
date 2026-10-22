"""Independently consume restored P5 stale HTTP, whole snapshots and anchored lease records."""
import argparse
import hashlib
import hmac
import json
from pathlib import Path

from d15_combination_runtime import digest, save
from verify_d13_restored_stale import conservation, exports, http, pending, load


def canonical(value):
    return (json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':')) + '\n').encode()


def tagged(value, anchor, field='anchorTag'):
    body = {key: item for key, item in value.items() if key != field}
    assert hmac.compare_digest(value[field], hmac.new(anchor, canonical(body), hashlib.sha256).hexdigest())


def control(attempt):
    home = attempt / 'private/p5-stale-authority'
    config = load(home / 'config.json'); root = Path(config['root'])
    assert root.is_relative_to(home) and Path(config['restore']) == attempt / 'data'
    identity = load(root / 'identity.json')
    assert identity['authorityKind'] == 'INITIAL_NORMAL_INSTALL' and identity['formatVersion'] == 2
    assert digest(root / 'identity.json') == config['identitySha256']
    assert load(root / 'writers.json')['identitySha256'] == config['identitySha256']
    records = [load(path) for path in sorted((root / 'history').glob('*.json'))]
    assert [row['revision'] for row in records] == ['1', '2']
    previous = '0' * 64
    for row in records:
        assert row['previousRecordSha256'] == previous
        assert hashlib.sha256(canonical(row['command'])).hexdigest() == row['commandSha256']
        body = {key: value for key, value in row.items() if key != 'recordSha256'}
        assert hashlib.sha256(canonical(body)).hexdigest() == row['recordSha256']
        previous = row['recordSha256']
    assert load(root / 'current.json')['recordSha256'] == previous
    assert load(Path(config['checkpoint']))['recordSha256'] == records[0]['recordSha256']
    advance = load(home / 'advance.json')
    assert {key: value for key, value in advance.items() if key not in
            ('testProducerPid', 'exclusiveWriterHeld', 'coordinatorHeld')} == records[1]
    assert advance['exclusiveWriterHeld'] and advance['coordinatorHeld']
    assert records[1]['command']['phase'] == 'RECOVERING'
    return dict(revision='2', producer='Separate IT JVM synthetic fixture with production coordinator/exclusive writer',
                producerPid=advance['testProducerPid'], independentPythonProducer=False, config=config)


def processes(attempt, authority, scope='restored-p5-stale', expected='CONTROL_INSTANCE_STALE'):
    home = attempt / ('private/' + scope)
    config = authority['config']; anchor = bytes.fromhex(config['anchor'])
    p5 = Path(config['lease']); assert p5.is_relative_to(attempt / 'private')
    assert digest(p5 / 'lease-root.json') == config['markerSha256']
    marker = load(p5 / 'lease-root.json')
    assert marker['anchorId'] == hashlib.sha256(anchor).hexdigest()
    before = load(p5.parent / 'before-p5.json'); after = load(p5.parent / 'after-p5.json')
    assert before == after
    actual = {path.relative_to(p5).as_posix(): dict(bytes=path.stat().st_size, sha256=digest(path))
              for path in p5.rglob('*') if path.is_file()}
    assert after == actual
    index = load(p5 / 'leases/index.json'); tagged(index, anchor, 'indexTag')
    assert not index['entries'] and [path.name for path in (p5 / 'leases').iterdir()] == ['index.json']
    pids = verify_owners(home, p5, anchor)
    assert authority['producerPid'] not in pids
    stale = load(home / 'worker-stale.json')
    assert stale['pid'] == pids[1] and stale['productionPollCalled'] and stale['productionReminderCalled']
    assert stale['admissionCode'] == expected and stale['liveRoots'] == 0
    log = (home / 'worker.stdout').read_text('utf-8', errors='replace')
    assert 'Background worker poll failed (RecoveryControlException)' in log
    assert 'Background reminder scheduling failed (RecoveryControlException)' in log
    return dict(pids=pids, p5=True, crossProcessEffectGate=True, liveLeaseObservedEachJvm=True,
                staleP5TreeInvariant=True, originalRecords=2, finalLeases=0, normalShutdown=True)


def verify_owners(home, p5, anchor):
    pids = []
    for role in ('api', 'worker'):
        ready = load(home / (role + '-ready.json')); observed = load(home / (role + '-p5-live.json'))
        assert ready['role'] == role and ready['web'] == (role == 'api') and observed['role'] == role
        assert ready['pid'] == observed['pid'] == observed['process']['pid']
        assert observed['phase'] == 'SERVING' and observed['retiredOwnOperation']
        lease, process = observed['lease'], observed['process']
        tagged(lease, anchor); tagged(process, anchor); tagged(observed['index'], anchor, 'indexTag')
        assert lease['operationId'] in observed['index']['entries']
        assert hashlib.sha256(canonical(process)).hexdigest() == lease['processSha256']
        assert load(p5 / ('processes/' + lease['processIdentity'] + '.json')) == process
        assert (home / (role + '-closed.marker')).is_file()
        options = load(home / (role + '-options.json'))
        for option in ('--outbound.recovery.lease.enabled=true', '--outbound.recovery.effect-gate.enabled=true',
                       '--outbound.recovery.effect-gate.cross-process-enabled=true'):
            assert option in options
        assert any(option.startswith('--spring.datasource.url=') and '_restore?' in option for option in options)
        pids.append(ready['pid'])
    assert len(set(pids)) == 2 and (home / 'worker-paused.marker').is_file()
    assert {load(path)['pid'] for path in (p5 / 'processes').glob('*.json')} == set(pids)
    return pids


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--attempt', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args(); attempt = args.attempt.resolve()
    assert (attempt / 'test-completed.marker').is_file()
    assert load(attempt / 'mysql-stopped.json')['exitCode'] == 0
    authority = control(attempt)
    result = dict(conservation=conservation(attempt), exports=exports(attempt), pending=pending(attempt),
                  http=http(attempt), processes=processes(attempt, authority),
                  control={key: value for key, value in authority.items() if key != 'config'},
                  dumpSHA256=digest(attempt / 'private/final-dump.stdout'))
    save(args.output, result)
    print(json.dumps(dict(status='PASS', output=str(args.output))))


if __name__ == '__main__':
    main()
