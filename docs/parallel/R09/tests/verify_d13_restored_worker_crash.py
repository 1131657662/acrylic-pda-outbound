"""Consume actual restored worker halt, retained anchored lease and API refusals offline."""
import argparse
import hashlib
from pathlib import Path

from d15_combination_runtime import digest, save
from verify_d13_restored_stale import conservation, exports, pending, load
from verify_d13_restored_p5_stale import canonical, tagged


def control(attempt):
    home = attempt / 'private/p5-stale-authority'; config = load(home / 'config.json')
    root = Path(config['root']); assert root.is_relative_to(home) and Path(config['restore']) == attempt / 'data'
    assert digest(root / 'identity.json') == config['identitySha256']
    identity = load(root / 'identity.json')
    assert identity['authorityKind'] == 'INITIAL_NORMAL_INSTALL' and identity['formatVersion'] == 2
    assert identity['restoreRoots'] == [config['restore']]
    records = [load(p) for p in sorted((root / 'history').glob('*.json'))]
    assert len(records) == 1 and records[0]['revision'] == '1'
    row = records[0]; body = {k: v for k, v in row.items() if k != 'recordSha256'}
    assert hashlib.sha256(canonical(body)).hexdigest() == row['recordSha256']
    assert hashlib.sha256(canonical(row['command'])).hexdigest() == row['commandSha256']
    assert load(root / 'current.json')['recordSha256'] == row['recordSha256']
    assert load(Path(config['checkpoint']))['revision'] == '1'
    return config


def process_records(attempt, config):
    home = attempt / 'private/restored-p5-crash'; p5 = Path(config['lease'])
    anchor = bytes.fromhex(config['anchor'])
    assert digest(p5 / 'lease-root.json') == config['markerSha256']
    pids = []
    for role, file in [('api', 'api-p5-live.json'), ('worker', 'worker-crash-held.json')]:
        live = load(home / file); ready = load(home / (role + '-ready.json'))
        assert live['pid'] == ready['pid'] == live['process']['pid'] and live['phase'] == 'SERVING'
        assert ready['web'] == (role == 'api')
        tagged(live['lease'], anchor); tagged(live['process'], anchor); tagged(live['index'], anchor, 'indexTag')
        assert live['lease']['operationId'] in live['index']['entries']
        assert hashlib.sha256(canonical(live['process'])).hexdigest() == live['lease']['processSha256']
        assert load(p5 / ('processes/' + live['lease']['processIdentity'] + '.json')) == live['process']
        pids.append(live['pid'])
    assert len(set(pids)) == 2
    return pids


def orphan(attempt, config):
    home = attempt / 'private/restored-p5-crash'; p5 = Path(config['lease']); anchor = bytes.fromhex(config['anchor'])
    held = load(home / 'worker-crash-held.json'); lease = held['lease']; operation = lease['operationId']
    assert not (p5 / ('leases/' + operation + '.json')).exists()
    index = load(p5 / 'leases/index.json'); tagged(index, anchor, 'indexTag'); assert index['entries'] == []
    assert {p.name for p in (p5 / 'leases').iterdir()} == {'index.json'}
    before = load(p5.parent / 'before-crash-p5.json'); after = load(p5.parent / 'after-crash-p5.json')
    actual = {p.relative_to(p5).as_posix(): dict(bytes=p.stat().st_size, sha256=digest(p)) for p in p5.rglob('*') if p.is_file()}
    assert before['leases/' + operation + '.json']['sha256'] == hashlib.sha256(canonical(lease)).hexdigest()
    assert {k:v for k,v in before.items() if k not in ('leases/' + operation + '.json','leases/index.json')} == {k:v for k,v in after.items() if k != 'leases/index.json'}
    successor = load(p5.parent / 'after-successor-p5.json'); assert successor == actual
    assert all(successor[k] == v for k,v in after.items())
    assert load(home / 'owner-before.json')['lifecycleLockHeld'] is True
    assert load(home / 'owner-after.json')['lifecycleLockHeld'] is False
    exit = load(home / 'worker-crash-exit.json')
    assert exit['pid'] == held['pid'] and exit['actualExitCode'] == 71 and not exit['aliveAfter'] and not exit['normalShutdown']
    assert not (home / 'worker-closed.marker').exists() and (home / 'api-closed.marker').is_file()
    assert (home / 'api-live-accepted.marker').is_file()
    refused = load(home / 'api-crash-refused.json')
    assert refused['admissionCode'] == refused['effectCode'] == 'RECOVERY_LEASE_FENCED' and refused['liveRoots'] == 0
    assert len(list((p5 / 'processes').glob('*.json'))) == 3 and len(successor) == len(after) + 1
    assert 'STARTED' in (home / 'successor.stdout').read_text(encoding='utf8')
    return dict(actualHaltExit=71,normalWorkerShutdown=False,originalLeaseCaptured=operation,orphanRetiredOnly=True,osLifecycleLockReleased=True,freshStartupAccepted=True)


def http(attempt):
    expected = {'pending': [401, 200, 200] + [200, 200] * 2,
                'restored-stale': [401, 200, 200, 404] + [200, 200, 200, 409, 200, 200] * 2,
                'stale': [200] * 5 + [503] * 6}
    codes = []
    for phase, statuses in expected.items():
        actual = []
        for p in sorted((attempt / ('private/http-' + phase)).glob('*.http.json')):
            meta = load(p); body = p.with_name(p.name.replace('.http.json', '.body'))
            assert digest(body) == meta['sha256'] and meta['status'] == meta['expected']
            actual.append(meta['status'])
            if meta['status'] == 503:codes.append(load(body)['code'])
        assert actual == statuses
    assert codes == ['RECOVERY_LEASE_OUTSTANDING'] + ['RECOVERY_LEASE_FENCED'] * 5
    return dict(statuses=expected,codes=codes)


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--attempt', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True); args = parser.parse_args(); attempt = args.attempt.resolve()
    assert (attempt / 'test-completed.marker').is_file() and load(attempt / 'mysql-stopped.json')['exitCode'] == 0
    config = control(attempt)
    pending_result = pending(attempt);pending_result.pop('pendingAfterExplicitOldWorkerCalls');pending_result['pendingAfterCrashRefusals'] = True
    result = dict(conservation=conservation(attempt),exports=exports(attempt),pending=pending_result,http=http(attempt),
                  pids=process_records(attempt,config),orphan=orphan(attempt,config),dumpSHA256=digest(attempt/'private/final-dump.stdout'),
                  syntheticJavaAuthority=True,pythonProducer=False,actualWrongDatabaseCovered=False)
    save(args.output,result);print('PASS: restored worker crash original consumer')


if __name__ == '__main__':
    main()
