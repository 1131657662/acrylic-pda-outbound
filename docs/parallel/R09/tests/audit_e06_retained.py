"""Audit historical E06 evidence; never promote partial history to combined acceptance."""
import argparse
import subprocess
import traceback
from pathlib import Path
from e06_retained_audit_io import BASE, ROOT, INPUTS, bindings, load, read, write
import e06_retained_native_audit
import e06_retained_service_audit


def audit(directory, reuse):
    sources = [bindings(BASE / name) for name in [
        'e06-l2-bindings01/candidate-inputs.json', 'e06-multitask-unit01/source-before.json',
        'e06-l2-android01/source-before.json']]
    assert not sources[0]['changed'], 'Current direct E06 consumers drifted; determine impact first'
    write(directory, 'source-bindings.json', sources)
    service = e06_retained_service_audit.audit()
    write(directory, 'service-audit.json', service)
    native = e06_retained_native_audit.audit(directory, reuse)
    write(directory, 'native-audit.json', native)
    return historical_checks()


def historical_checks():
    checks = {name: load(BASE / name) for name in [
        'e06-l2-android01/result.json', 'e06-l2-web-checks01/result.json',
        'e06-l2-web-consumers-result02.json', 'e06-l2-closure-vector01/result.json']}
    jar = ROOT / '.local/goal-20261013/e06-conflict-audit-1539/server.jar'
    original_binding = load(BASE / 'e06-l2-bindings01/result.json')
    retained = retained_jar(jar, original_binding['serviceJarSha256'])
    for name in ['e06-l2-http-1539.py', 'e06-l2-independent1539.py', 'e06-multitask-lock-1537-l2-1539.py',
                 'e06-l2-native-facts.py', 'e06-l2-original-consumer02.py', 'e06-multitask-common-1539.py',
                 'e06-multitask-fixture-1539.py', 'e06-multitask-restore-1539.py']:
        read(BASE / name)
    for path in Path(__file__).parent.glob('*e06_retained*.py'):
        read(path)
    return {**checks, 'jarRetention': retained}


def retained_jar(original, expected):
    from e06_retained_audit_io import digest
    if original.exists():
        assert digest(read(original)) == expected
        return {'original': str(original), 'sha256': expected}
    cleanup = ROOT / '.local/disk-cleanup-20261016-round4/duplicate-jar-plan.json'
    entry = next(row for row in load(cleanup) if Path(row['path']) == original)
    raw = read(entry['retained'])
    assert digest(raw) == expected == entry['sha256'] and len(raw) == entry['bytes']
    return {'original': str(original), 'retained': entry['retained'], 'sha256': expected,
            'cleanupManifest': str(cleanup), 'sameBytesVerified': True, 'restoredOrStarted': False}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True)
    parser.add_argument('--reuse-native-evidence')
    args = parser.parse_args()
    directory = Path(args.output).resolve()
    assert directory.is_relative_to(ROOT / '.local'), directory
    directory.mkdir()
    reuse = Path(args.reuse_native_evidence).resolve() if args.reuse_native_evidence else None
    assert reuse is None or reuse.is_relative_to(ROOT / '.local')
    try:
        checks = audit(directory, reuse)
    except Exception:
        (directory / 'failure.txt').write_text(traceback.format_exc(), encoding='utf-8')
        write(directory, 'inputs-on-failure.json', INPUTS)
        raise
    write(directory, 'inputs.json', INPUTS)
    result = {'passed': True, 'kind': 'offline-history-audit-and-current-python-positive',
              'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT).decode().strip(),
              'historicalChecks': checks, 'inputFiles': len(INPUTS), 'e06L2Complete': False,
              'chromeBlocked': True, 'physicalDevice': False, 'databasesOpened': 0, 'devicesOpened': 0}
    write(directory, 'result.json', result)
    print(result)


if __name__ == '__main__':
    main()
