"""Reconsume the actual retained E06 SAF package using the product Python tools."""
import json
import subprocess
import sys
from e06_retained_audit_io import BASE, ROOT, digest, load, read


def original_database():
    base = BASE / 'e06-l2-native-read01'
    before = load(base / 'before/facts.json')
    assert before == load(base / 'after/facts.json')
    for side in ['before', 'after']:
        for table, row in before.items():
            raw = read(base / side / (table + '.rows.jsonl'))
            assert len(raw.splitlines()) == row['rows']
            assert digest(raw) == row['sha256']
    originals = load(base / 'before/files.json')
    after = load(base / 'after/files.json')
    retained = {path: value for path, value in originals.items() if path.startswith('files/independent-')}
    for path, value in retained.items():
        assert after[path] == value
        for side in ['before', 'after']:
            raw = read(base / side / 'app' / path)
            assert len(raw) == value['size'] and digest(raw) == value['sha256']
    return {'unchangedTables': len(before), 'unchangedEarlierFiles': len(retained)}


def instrument():
    base = BASE / 'e06-l2-native-instrument01'
    command = load(base / 'command.json')
    assert command[1] == 'emulator-5562'
    assert load(base / 'result.json')['passed'] is True
    assert b'OK (1 test)' in read(base / 'instrument.log')
    result = load(BASE / 'e06-l2-native-read01/after/app/files/e06-l2-native-restart01/result.json')
    assert result['offlineNewProcess'] and result['originalFactsUnchanged']
    assert not result['physicalDevice'] and not result['localDeletionAllowed']
    binding = load(BASE / 'e06-l2-native-build01/input.json')
    assert digest(read(binding['Path'])).lower() == binding['Hash'].lower()
    return {'historicalEmulator': 'emulator-5562', 'tests': 1, 'sourceSha256': binding['Hash'], 'physicalDevice': False}


def computer(directory):
    sys.path.insert(0, str(ROOT))
    from tools.pda_rescue.cli import publish
    from tools.pda_rescue.conversion import convert
    from tools.pda_rescue.envelope import decrypt
    from tools.pda_rescue.integrity import parse
    from tools.pda_rescue.verify import verify
    source = BASE / 'e06-l2-original-consumer02'
    package = source / 'actual-saf-copy.pdarescue'
    assert digest(read(package)) == 'b979d685981a6ea6f6e0db37874cf917057d54ee7ede0c3a609591ca5fd86966'
    plain = decrypt(package, 'synthetic-package-passphrase-only')
    assert plain == read(source / 'actual-original.zip')
    trust = read(ROOT / '.local/goal-20261011/e03-formal-environment-1383/external-trust.json')
    result = verify(plain, parse(trust.removeprefix(b'\xef\xbb\xbf')))
    assert result['sourceTrusted'] and not result['businessReceiptVerified']
    assert result['facts']['independent']['pendingReconciliation']
    publish(result, directory / 'computer-output', convert)
    args = [sys.executable, str(ROOT / '.local/goal-20261013/e06-export-computer-check.py'), str(directory / 'computer-output')]
    process = subprocess.run(args, capture_output=True, timeout=120, cwd=ROOT)
    (directory / 'computer-check.stdout').write_bytes(process.stdout)
    (directory / 'computer-check.stderr').write_bytes(process.stderr)
    assert process.returncode == 0, 'New computer output checker failed; original evidence retained'
    return {'actualConsumerExit': process.returncode, 'plainSha256': digest(plain), 'args': args,
            'originalSafBytes': True, 'externalRegistrationTrusted': True, 'physicalDevice': False}


def retained_computer(directory):
    native = load(directory / 'native-audit.json')['computer']
    assert native['actualConsumerExit'] == 0 and native['originalSafBytes']
    result = load(directory / 'computer-output/independent-xlsx-check.json')
    assert result['passed'] and result['assertions'] == 460 and not result['nativeExcel']
    for path in (directory / 'computer-output').iterdir():
        if path.is_file():
            read(path)
    return {**native, 'reusedStageDirectory': str(directory), 'independentXlsxAssertions': result['assertions']}


def audit(directory, reuse=None):
    consumed = retained_computer(reuse) if reuse else computer(directory)
    return {'database': original_database(), 'instrumentation': instrument(), 'computer': consumed}
