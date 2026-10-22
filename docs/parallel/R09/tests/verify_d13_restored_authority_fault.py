"""Consume complete restored authority-fault originals without running the product again."""
import argparse
import hashlib
import json
from pathlib import Path

from d15_combination_runtime import digest, save
from verify_d13_restored_stale import conservation, exports, pending, load
from verify_d13_restored_p5_stale import canonical, processes


def control(attempt):
    home = attempt / 'private/p5-stale-authority'
    config = load(home / 'config.json'); root = Path(config['root'])
    assert root.is_relative_to(home) and Path(config['restore']) == attempt / 'data'
    fault = load(home / 'fault.json'); original = load(home / 'fault-original.bin')
    assert digest(home / 'fault-original.bin') == fault['originalSha256']
    target = Path(fault['target']); assert target.is_relative_to(home)
    assert digest(target) == fault['changedSha256'] and fault['originalPreserved'] and not fault['controlAdvanced']
    records = [load(path) for path in sorted((root / 'history').glob('*.json'))]
    assert len(records) == 1 and records[0]['revision'] == '1'
    row = records[0]; body = {k: v for k, v in row.items() if k != 'recordSha256'}
    assert hashlib.sha256(canonical(body)).hexdigest() == row['recordSha256']
    assert hashlib.sha256(canonical(row['command'])).hexdigest() == row['commandSha256']
    assert row['previousRecordSha256'] == '0' * 64
    assert load(root / 'current.json')['recordSha256'] == row['recordSha256']
    check_fault(config, fault, original, row)
    return dict(config=config, producerPid=fault['producerPid'], kind=fault['kind'], code=fault['code'],
                controlAdvanced=False, independentPythonProducer=False, exactOriginalPreserved=True)


def check_fault(config, fault, original, row):
    root = Path(config['root']); identity = load(root / 'identity.json')
    assert identity['formatVersion'] == 2 and identity['authorityKind'] == 'INITIAL_NORMAL_INSTALL'
    if fault['kind'] == 'restore-roots':
        assert fault['code'] == 'CONTROL_RESTORE_SET_MISMATCH'
        assert fault['originalSha256'] == config['identitySha256']
        assert {k: v for k, v in identity.items() if k != 'restoreRoots'} == {k: v for k, v in original.items() if k != 'restoreRoots'}
        assert original['restoreRoots'] == [config['restore']] and identity['restoreRoots'] != original['restoreRoots']
        assert load(Path(config['checkpoint']))['revision'] == '1'
    else:
        assert fault['kind'] == 'checkpoint' and fault['code'] == 'CONTROL_CHECKPOINT_ROLLBACK'
        changed = load(Path(config['checkpoint']))
        assert changed == dict(original, revision='2') and original['revision'] == '1'
        assert original['recordSha256'] == row['recordSha256']
        assert digest(root / 'identity.json') == config['identitySha256']
    assert load(root / 'writers.json')['identitySha256'] == config['identitySha256']


def http(attempt, code):
    expected = {'pending': [401, 200, 200] + [200, 200] * 2,
                'restored-stale': [401, 200, 200, 404] + [200, 200, 200, 409, 200, 200] * 2,
                'stale': [200, 200, 200, 200, 200] + [503] * 6}
    for phase, statuses in expected.items():
        actual = []
        for path in sorted((attempt / ('private/http-' + phase)).glob('*.http.json')):
            meta = load(path); body = path.with_name(path.name.replace('.http.json', '.body'))
            assert digest(body) == meta['sha256'] and meta['status'] == meta['expected']
            actual.append(meta['status'])
            if meta['status'] == 503:
                assert load(body)['code'] == code
        assert actual == statuses, (phase, actual)
    return expected


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--attempt', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args(); attempt = args.attempt.resolve()
    assert (attempt / 'test-completed.marker').is_file()
    assert load(attempt / 'mysql-stopped.json')['exitCode'] == 0
    authority = control(attempt)
    result = dict(conservation=conservation(attempt), exports=exports(attempt), pending=pending(attempt),
                  http=http(attempt, authority['code']),
                  processes=processes(attempt, authority, 'restored-p5-fault', authority['code']),
                  control={k: v for k, v in authority.items() if k != 'config'},
                  dumpSHA256=digest(attempt / 'private/final-dump.stdout'))
    save(args.output, result)
    print(json.dumps(dict(status='PASS', output=str(args.output))))


if __name__ == '__main__':
    main()
