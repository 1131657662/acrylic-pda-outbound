"""Offline consumer of complete restored closed-admission originals; no database or network."""
import argparse
import base64
import json
from pathlib import Path
import uuid

from d13_export_file_consume import pdf, xlsx
from d15_combination_runtime import digest, save
from verify_d13_restored_scheduler import stable_schema


def load(path):
    return json.loads(path.read_bytes())


def inputs(args):
    run = load(args.run_output / 'test-result.json')
    assert run['exitCode'] == 0 and run['completed']
    assert run['counts'] == ['COUNTS found=1 started=1 succeeded=1 failed=0 skipped=0 aborted=0']
    allocation = load(args.run_output / 'allocation.json')
    assert Path(allocation['attempt']).resolve() == args.attempt.resolve()
    manifest = load(args.run_output / 'output-hashes.json')
    for name, fact in manifest.items():
        path = args.attempt / name
        assert path.stat().st_size == fact['bytes'] and digest(path) == fact['sha256']
    frozen = load(args.compiled / 'source-inputs.json')
    for item in frozen:
        assert digest(args.compiled / 'source' / item['path']).upper() == item['sha256']
    dependencies = load(args.compiled / 'dependencies.json')
    assert all(digest(Path(item['path'])).upper() == item['sha256'] for item in dependencies)
    assert load(args.attempt / 'mysql-stopped.json')['exitCode'] == 0
    assert (args.attempt / 'identity-before.stdout').read_bytes() == (args.attempt / 'identity-final.stdout').read_bytes()
    return dict(sourceFiles=len(frozen), dependencies=len(dependencies), originalOutputs=len(manifest))


def conservation(attempt):
    private = attempt / 'private'
    before, admitted, final = [load(private / name) for name in
        ('restored-before.json', 'restored-admitted.json', 'restored-final.json')]
    assert before == load(private / 'source-final.json') == load(private / 'archive-snapshot-facts.json')
    assert load(private / 'refusal-before.json') == load(private / 'refusal-after.json')
    protocol = {'server_state', 'recovery_installation_binding', 'recovery_control_epoch',
                'recovery_control_projection', 'recovery_normal_admission'}
    changed = [name for name in before['tables'] if before['tables'][name] != admitted['tables'][name]]
    assert set(changed) == protocol
    assert set(before['tables']) == set(admitted['tables']) == set(final['tables'])
    assert all(before[key] == admitted[key] for key in ('schema', 'flywayHistory', 'files'))
    runtime = {'background_job', 'operation_event', 'spring_session', 'spring_session_attributes'}
    altered = [name for name in admitted['tables'] if admitted['tables'][name] != final['tables'][name]]
    assert set(altered) <= runtime
    assert stable_schema(admitted) == stable_schema(final) and admitted['flywayHistory'] == final['flywayHistory']
    assert all(final['files'].get(name) == fact for name, fact in before['files'].items())
    for name, fact in final['files'].items():
        path = attempt / 'restored/storage' / name
        assert digest(path) == fact['sha256'] and path.stat().st_size == fact['bytes']
    for name in ('server-private.der', 'server-public.der'):
        assert digest(attempt / 'private' / name) == digest(attempt / 'restored/config' / name)
    assert all(row in final['tables']['background_job']['rows'] for row in before['tables']['background_job']['rows'])
    assert len(final['tables']['background_job']['rows']) == 5
    return dict(tables=len(before['tables']), registrationChanged=changed, runtimeChanged=altered,
                sourceInvariant=True, originalJobsAllColumnsInvariant=True, refusalAllFactsInvariant=True)


def exports(attempt):
    final = load(attempt / 'private/restored-final.json')['tables']['background_job']
    rows = {str(uuid.UUID(bytes=base64.b64decode(row[0]))): dict(zip(final['columns'], row, strict=True))
            for row in final['rows']}
    result = {}
    for extension in ('xlsx', 'pdf'):
        original = load(attempt / ('pending-' + extension + '.json'))
        prefix = 'registered-restored-' + extension
        record = load(attempt / (prefix + '.json'))
        view, payload = record['view'], record['payload']
        assert record['id'] == view['id'] and record['id'] != original['id'] == record['originalId']
        assert record['key'] != original['key'] and payload['epoch'] != original['payload']['epoch']
        assert view['status'] == 'SUCCEEDED' and view['attempts'] == 1 and view['downloadable']
        row = rows[original['id']]
        assert base64.b64decode(row['status']) == b'PENDING' and base64.b64decode(row['attempt_count']) == b'0'
        snapshot, file = attempt / (prefix + '.snapshot.json'), attempt / (prefix + '.' + extension)
        assert digest(snapshot) == payload['snapshotHash']
        assert digest(file) == view['sha256'] == payload['artifact']['sha256']
        assert file.stat().st_size == view['bytes'] == payload['artifact']['bytes']
        old = attempt / 'storage/exports/snapshots' / (original['payload']['snapshotHash'] + '.json')
        assert load(snapshot)['orders'] == load(old)['orders']
        parsed = pdf(file, load(snapshot)) if extension == 'pdf' else xlsx(file, load(snapshot))
        result[extension] = dict(sha256=digest(file), originalJobInvariant=True, regenerated=True, **parsed)
    return result


def http(attempt):
    result = {}
    for phase in ('pending', 'regenerated', 'stale'):
        statuses, codes = [], []
        for path in sorted((attempt / ('private/http-' + phase)).glob('*.http.json')):
            fact = load(path); body = path.with_name(path.name.replace('.http.json', '.body'))
            assert fact['status'] == fact['expected'] and digest(body) == fact['sha256']
            statuses.append(fact['status'])
            if fact['status'] in (409, 503):
                codes.append(load(body)['code'])
        assert statuses
        result[phase] = dict(statuses=statuses, codes=codes)
    assert result['stale']['statuses'][-6:] == [503] * 6
    assert result['stale']['codes'] == ['RECOVERY_DATABASE_ADMISSION_REFUSED'] * 6
    for code in ('SERVER_RECOVERY_EPOCH_MISMATCH', 'EXPORT_GENERATION_CHANGED', 'IDEMPOTENCY_CONFLICT'):
        assert result['regenerated']['codes'].count(code) >= 4
    return result


def control(attempt):
    import hashlib
    def hashed(value):
        raw = (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')) + '\n').encode()
        return hashlib.sha256(raw).hexdigest()
    home = attempt / 'private/restored-db-admission'
    source, fixture = load(home / 'source-input.json'), load(home / 'projection/fixture.json')
    root = Path(fixture['root']); previous = '0' * 64; records = []
    for path in sorted((root / 'history').glob('*.json')):
        record = load(path); body = {key: value for key, value in record.items() if key != 'recordSha256'}
        assert hashed(body) == record['recordSha256'] and hashed(record['command']) == record['commandSha256']
        assert record['previousRecordSha256'] == previous
        previous = record['recordSha256']; records.append(record)
    assert [row['command']['phase'] for row in records] == ['QUIESCING', 'RECOVERING', 'VERIFYING', 'RELEASE_READY']
    assert records[0]['command']['manifestSha256'] == source['source']['manifestSha256'] == digest(attempt / 'private/archive-snapshot.sql')
    assert records[0]['command']['evidenceSha256'] == digest(home / 'source-input.json')
    intent = load(root / 'normal-admission.json')
    assert hashed({key: value for key, value in intent.items() if key != 'intentSha256'}) == intent['intentSha256']
    assert intent['controlRecordSha256'] == previous == load(root / 'current.json')['recordSha256']
    assert source['syntheticAuthority'] and source['restoredSourceWasUnbound']
    assert load(home / 'registered.json')['outcome'] == 'REGISTERED' and load(home / 'admitted.json')['admitted']
    originals = load(attempt / 'private/authority-originals.json')
    assert {str(path.relative_to(home)): digest(path) for path in home.rglob('*') if path.is_file()} == originals
    return dict(synthetic=True, previouslyUnbound=True, phases=4, originals=len(originals), hashChainVerified=True)


def processes(attempt, run_output):
    home = attempt / 'private/restored-split-successor'; pids = []
    for role in ('api', 'worker'):
        ready = load(home / (role + '-ready.json'))
        assert ready['role'] == role and ready['web'] == (role == 'api')
        assert (home / (role + '-closed.marker')).is_file()
        options = load(home / (role + '-options.json'))
        assert '--outbound.recovery.admission.enabled=true' in options
        assert any(option.startswith('--spring.datasource.url=') and '_restore?' in option for option in options)
        pids.append(ready['pid'])
    assert len(set(pids)) == 2
    wrong = load(attempt / 'private/wrong-schema-startup.json')
    startup_log = run_output / 'tests.stdout'
    raw = startup_log.read_text('utf-8-sig')
    assert raw.count('Application run failed') == 1
    failure = raw.split('Application run failed', 1)[1]
    assert wrong['sourceUnchanged'] is True and wrong['exception'] in failure
    prefix = 'Caused by: com.acrylic.outbound.recovery.RecoveryDatabaseAdmissionException: '
    causes = [line.strip() for line in failure.splitlines() if line.startswith(prefix)]
    assert len(causes) == 1 and causes[0].startswith(prefix + 'RECOVERY_DATABASE_ADMISSION_REFUSED:')
    gaps = causes[0].split('RECOVERY_DATABASE_ADMISSION_REFUSED:', 1)[1].split(',')
    assert set(gaps) == {'DATABASE_BINDING_MISSING', 'DATABASE_IDENTITY_MISSING', 'DURABLE_INTENT_SCHEMA',
                         'DURABLE_INTENT_BASELINE', 'NORMAL_ADMISSION_MISSING', 'PROJECTION_MISSING'}
    return dict(distinctPids=pids, normalShutdown=True, wrongSchemaStartupRefused=True, p5=False,
                wrongSchemaGaps=sorted(gaps), startupLogSha256=digest(startup_log))


def main():
    parser = argparse.ArgumentParser()
    for name in ('attempt', 'run-output', 'compiled', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    proof = dict(inputs=inputs(args), conservation=conservation(args.attempt), exports=exports(args.attempt),
                 http=http(args.attempt), control=control(args.attempt),
                 processes=processes(args.attempt, args.run_output))
    save(args.output, proof)
    print(json.dumps(dict(status='PASS', tables=proof['conservation']['tables'], output=str(args.output))))


if __name__ == '__main__':
    main()
