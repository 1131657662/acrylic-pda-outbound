"""Consume exact successor restore/API/worker/P5/file originals without database or network."""
import argparse
from pathlib import Path

from d15_combination_runtime import digest, save
from d13_successor_fact_consume import binding, normal, original_hash, rows
from verify_d13_restored_closed import exports, http, load
from verify_d13_restored_closed_p5 import hashed, p5
from verify_d13_restored_scheduler import stable_schema


def inputs(args):
    result = load(args.run_output / 'result.json')
    assert result['exitCode'] == 0
    assert result['counts'] == ['COUNTS found=1 started=1 succeeded=1 failed=0 skipped=0 aborted=0']
    assert (args.attempt / 'test-completed.marker').is_file()
    assert Path(load(args.run_output / 'allocation.json')['attempt']).resolve() == args.attempt.resolve()
    manifest = load(args.run_output / 'output-hashes.json')
    for name, fact in manifest.items():
        path = args.attempt / name
        assert path.stat().st_size == fact['bytes'] and digest(path) == fact['sha256']
    sources = load(args.compiled / 'source-inputs.json')
    assert all(digest(args.compiled / 'source' / fact['path']) == fact['sha256'] for fact in sources)
    dependencies = load(args.compiled / 'dependencies.json')
    assert all(digest(Path(fact['path'])) == fact['sha256'] for fact in dependencies)
    assert load(args.attempt / 'mysql-stopped.json')['exitCode'] == 0
    assert (args.attempt / 'identity-before.stdout').read_bytes() == (args.attempt / 'identity-final.stdout').read_bytes()
    return dict(sourceFiles=len(sources), dependencies=len(dependencies), originalOutputs=len(manifest))


def registration(before, after):
    appended = dict(recovery_control_projection=4, recovery_control_epoch=1,
                    recovery_successor_binding=1, recovery_successor_normal=1)
    changed = {name for name in before['tables'] if before['tables'][name] != after['tables'][name]}
    assert changed == set(appended) | {'server_state'}
    for name, addition in appended.items():
        old, new = [fact['tables'][name] for fact in (before, after)]
        assert old['columns'] == new['columns'] and all(row in new['rows'] for row in old['rows'])
        assert len(new['rows']) == len(old['rows']) + addition
    old, new = [rows(fact, 'server_state')[0] for fact in (before, after)]
    assert old['service_mode'] == new['service_mode'] == b'NORMAL'
    assert int(new['recovery_epoch']) == int(old['recovery_epoch']) + 1
    assert {key: val for key, val in old.items() if key != 'recovery_epoch'} == {
        key: val for key, val in new.items() if key != 'recovery_epoch'}
    return sorted(changed)


def conservation(attempt):
    private = attempt / 'private'
    before, admitted, final = [load(private / name) for name in
                              ('restored-before.json', 'restored-admitted.json', 'restored-final.json')]
    assert before == load(private / 'archive-snapshot-facts.json') == load(private / 'source-final.json')
    assert load(private / 'refusal-before.json') == load(private / 'refusal-after.json')
    changed = registration(before, admitted)
    assert before['tables'].keys() == admitted['tables'].keys() == final['tables'].keys()
    assert all(before[key] == admitted[key] for key in ('schema', 'flywayHistory', 'files'))
    runtime = {'background_job', 'operation_event', 'spring_session', 'spring_session_attributes'}
    altered = {name for name in admitted['tables'] if admitted['tables'][name] != final['tables'][name]}
    assert altered <= runtime
    assert stable_schema(admitted) == stable_schema(final) and admitted['flywayHistory'] == final['flywayHistory']
    assert all(final['files'].get(name) == fact for name, fact in before['files'].items())
    for name, fact in final['files'].items():
        path = attempt / 'restored/storage' / name
        assert digest(path) == fact['sha256'] and path.stat().st_size == fact['bytes']
    for name in ('server-private.der', 'server-public.der'):
        assert digest(attempt / 'private' / name) == digest(attempt / 'restored/config' / name)
    assert all(row in final['tables']['background_job']['rows'] for row in before['tables']['background_job']['rows'])
    assert len(final['tables']['background_job']['rows']) == 5
    return dict(tables=len(before['tables']), registrationChanged=changed, runtimeChanged=sorted(altered),
                sourceInvariant=True, originalJobsAllColumnsInvariant=True, refusalAllFactsInvariant=True)


def history(root):
    records = []; previous = '0' * 64
    for path in sorted((root / 'history').glob('*.json')):
        record = load(path)
        assert hashed({key: val for key, val in record.items() if key != 'recordSha256'}) == record['recordSha256']
        assert hashed(record['command']) == record['commandSha256'] and record['previousRecordSha256'] == previous
        previous = record['recordSha256']; records.append(record)
    assert [row['command']['phase'] for row in records] == ['QUIESCING', 'RECOVERING', 'VERIFYING', 'RELEASE_READY'] * 2
    assert [row['command']['formatVersion'] for row in records] == [1] * 4 + [2] * 4
    assert load(root / 'current.json')['recordSha256'] == previous
    return records


def control(attempt):
    root = Path(load(attempt / 'successor-control/fixture.json')['root'])
    records = history(root); pins = load(attempt / 'source-pins.json')
    before, after = [load(attempt / 'private' / name) for name in ('restored-before.json', 'restored-admitted.json')]
    assert original_hash(before, 'recovery_installation_binding') == pins['sourceBindingSha256']
    assert original_hash(before, 'recovery_normal_admission') == pins['sourceNormalSha256']
    assert not pins['ordinaryWritesAllowed'] and not pins['localDeletionAllowed']
    initial = load(root / 'normal-admission.json'); candidates = list((root / 'normal-history').glob('*.json'))
    assert len(candidates) == 1
    intent = load(candidates[0])
    for value in (initial, intent):
        assert hashed({key: val for key, val in value.items() if key != 'intentSha256'}) == value['intentSha256']
    assert initial['intentSha256'] == pins['predecessorNormalIntentSha256']
    bound, normal_row = binding(after, records, pins), normal(after, records, intent)
    admitted = load(attempt / 'successor-admitted.json')
    assert admitted['admitted'] and not admitted['gaps']
    assert not admitted['ordinaryWritesAllowed'] and not admitted['localDeletionAllowed']
    originals = load(attempt / 'private/authority-originals.json')
    assert originals == {str(path.relative_to(root)): digest(path) for path in root.rglob('*') if path.is_file()}
    return dict(synthetic=True, previouslyBound=True, phases=8, sourcePinsVerified=True,
                bindingHash=hashed(bound), normalHash=hashed(normal_row), originals=len(originals))


def promotion(attempt):
    home = attempt / 'private/restored-admission-p5'; fact = load(home / 'promotion.json')
    root = Path(load(attempt / 'successor-control/fixture.json')['root'])
    before, after = fact['before'], fact['after']
    assert fact['syntheticTestOnly'] is True and fact['productProvisioning'] is False
    assert before.keys() == after.keys()
    assert {key for key in before if before[key] != after[key]} == {'identity.json', 'writers.json'}
    assert after == {str(path.relative_to(root)).replace('\\', '/'): digest(path) for path in root.rglob('*') if path.is_file()}
    for name in ('identity.json', 'writers.json'):
        assert digest(home / ('before-' + name)) == before[name]
        assert digest(home / ('after-' + name)) == after[name] == digest(root / name)
    old, new = [load(home / (label + '-identity.json')) for label in ('before', 'after')]
    assert old['formatVersion'] == 1 and new['formatVersion'] == 2
    extra = {'formatVersion', 'authorityKind', 'closedAuthority', 'leaseRoot'}
    assert {key: val for key, val in old.items() if key not in extra} == {
        key: val for key, val in new.items() if key not in extra}
    assert new['authorityKind'] == 'INITIAL_NORMAL_INSTALL' and hashed(new) == fact['identitySha256']
    old, new = [load(home / (label + '-writers.json')) for label in ('before', 'after')]
    assert {key: val for key, val in old.items() if key != 'identitySha256'} == {
        key: val for key, val in new.items() if key != 'identitySha256'}
    assert new['identitySha256'] == fact['identitySha256']
    return dict(testOnly=True, productProvisioning=False, retainedOriginalBytes=True)


def wrong_schema(args):
    raw = (args.run_output / 'tests.stdout').read_text('utf-8-sig')
    assert raw.count('Application run failed') == 1
    failure = raw.split('Application run failed', 1)[1]
    fact = load(args.attempt / 'private/wrong-schema-startup.json')
    assert fact['exception'] in failure
    prefix = 'Caused by: com.acrylic.outbound.recovery.RecoveryDatabaseAdmissionException: '
    causes = [line.strip() for line in failure.splitlines() if line.startswith(prefix)]
    assert len(causes) == 1
    gaps = causes[0].split('RECOVERY_DATABASE_ADMISSION_REFUSED:', 1)[1].split(',')
    assert set(gaps) == {'DATABASE_EVIDENCE_UNAVAILABLE'}
    return dict(gaps=gaps, sourceInvariant=True, startupLogSha256=digest(args.run_output / 'tests.stdout'))


def main():
    parser = argparse.ArgumentParser()
    for name in ('attempt', 'run-output', 'compiled', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    proof = dict(inputs=inputs(args), conservation=conservation(args.attempt), exports=exports(args.attempt),
        http=http(args.attempt), control=control(args.attempt), promotion=promotion(args.attempt),
        p5=p5(args.attempt), wrongSchema=wrong_schema(args))
    save(args.output, proof)
    print('PASS: bound successor restore/API/worker/P5/file originals consumed')


if __name__ == '__main__':
    main()
