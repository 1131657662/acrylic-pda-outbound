"""Offline consumer of restored production registration plus explicit synthetic P5 originals."""
import argparse
import hashlib
import hmac
import json
from pathlib import Path

from d15_combination_runtime import digest, save
from verify_d13_restored_closed import conservation, control, exports, http, inputs, load


def canonical(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')) + '\n').encode()


def hashed(value):
    return hashlib.sha256(canonical(value)).hexdigest()


def tag(value, anchor, field='anchorTag'):
    body = {key: item for key, item in value.items() if key != field}
    expected = hmac.new(anchor, canonical(body), hashlib.sha256).hexdigest()
    assert hmac.compare_digest(value[field], expected)


def promotion(attempt):
    home = attempt / 'private/restored-admission-p5'
    fact = load(home / 'promotion.json')
    fixture = load(attempt / 'private/restored-db-admission/projection/fixture.json')
    root = Path(fixture['root'])
    assert fact['syntheticTestOnly'] is True and fact['productProvisioning'] is False
    before, after = fact['before'], fact['after']
    assert before.keys() == after.keys()
    assert {key for key in before if before[key] != after[key]} == {'identity.json', 'writers.json'}
    assert after == {str(path.relative_to(root)).replace('\\', '/'): digest(path)
                     for path in root.rglob('*') if path.is_file()}
    for name in ('identity.json', 'writers.json'):
        assert digest(home / ('before-' + name)) == before[name]
        assert digest(home / ('after-' + name)) == after[name] == digest(root / name)
    identity_before, identity_after = [load(home / (label + '-identity.json')) for label in ('before', 'after')]
    extra = {'formatVersion', 'authorityKind', 'closedAuthority', 'leaseRoot'}
    assert identity_before['formatVersion'] == 1 and identity_after['formatVersion'] == 2
    assert {key: value for key, value in identity_before.items() if key not in extra} == {
        key: value for key, value in identity_after.items() if key not in extra}
    assert identity_after['authorityKind'] == 'INITIAL_NORMAL_INSTALL'
    descriptor_before, descriptor_after = [load(home / (label + '-writers.json')) for label in ('before', 'after')]
    assert {key: value for key, value in descriptor_before.items() if key != 'identitySha256'} == {
        key: value for key, value in descriptor_after.items() if key != 'identitySha256'}
    assert descriptor_after['identitySha256'] == fact['identitySha256'] == hashed(identity_after)
    return dict(testOnly=True, productProvisioning=False, changedPaths=['identity.json', 'writers.json'],
                retainedOriginalBytes=True, originals=len(before))


def options(home, role):
    values = load(home / (role + '-options.json'))
    result = dict(value[2:].split('=', 1) for value in values)
    assert len(result) == len(values)
    return result


def live(home, role, anchor, identity_sha):
    fact = load(home / (role + '-p5-live.json'))
    ready = load(home / (role + '-ready.json'))
    lease, process, index = [fact[key] for key in ('lease', 'process', 'index')]
    tag(lease, anchor); tag(process, anchor); tag(index, anchor, 'indexTag')
    assert fact['role'] == role and ready['role'] == role and ready['web'] == (role == 'api')
    assert fact['pid'] == ready['pid'] == process['pid']
    assert fact['retiredOwnOperation'] is True and fact['phase'] == 'SERVING'
    assert lease['operationId'] in index['entries'] and lease['processIdentity'] == process['processIdentity']
    assert lease['processSha256'] == hashed(process)
    assert lease['installationId'] == process['installationId'] == index['installationId']
    assert process['identitySha256'] == identity_sha
    assert (home / (role + '-closed.marker')).is_file()
    return dict(pid=fact['pid'], operationId=lease['operationId'], processIdentity=process['processIdentity'],
                leaseTagVerified=True, processTagVerified=True, indexTagVerified=True, retired=True, normalShutdown=True)


def p5(attempt):
    home = attempt / 'private/restored-closed-p5'
    supply = attempt / 'private/restored-admission-p5'
    api, worker = [options(home, role) for role in ('api', 'worker')]
    prefix = 'outbound.recovery.'
    for key in ('lease.root', 'lease.closed-authority', 'lease.identity-sha256', 'lease.marker-sha256',
                'effect-gate.anchor', 'root', 'checkpoint', 'installation-id'):
        assert api[prefix + key] == worker[prefix + key]
    for values in (api, worker):
        assert all(values[prefix + key] == 'true' for key in ('admission.enabled', 'lease.enabled',
                   'effect-gate.enabled', 'effect-gate.cross-process-enabled'))
        assert '_restore?' in values['spring.datasource.url']
    anchor = bytes.fromhex(api[prefix + 'effect-gate.anchor'])
    marker = load(supply / 'p5/lease-root.json')
    assert hashed(marker) == api[prefix + 'lease.marker-sha256']
    assert marker['anchorId'] == hashlib.sha256(anchor).hexdigest()
    assert marker['bindingSha256'] == hashed({key: value for key, value in marker.items() if key != 'bindingSha256'})
    identity_sha = api[prefix + 'lease.identity-sha256']
    roles = {role: live(home, role, anchor, identity_sha) for role in ('api', 'worker')}
    assert roles['api']['pid'] != roles['worker']['pid']
    return finish_p5(supply, roles, anchor)


def finish_p5(supply, roles, anchor):
    before, final = [load(supply / ('p5-' + label + '.json')) for label in ('before', 'final')]
    assert all(final[name] == sha for name, sha in before.items())
    assert final == {str(path.relative_to(supply / 'p5')).replace('\\', '/'): digest(path)
                     for path in (supply / 'p5').rglob('*') if path.is_file()}
    index = load(supply / 'p5/leases/index.json'); tag(index, anchor, 'indexTag')
    assert index['entries'] == []
    assert {path.name for path in (supply / 'p5/leases').iterdir()} == {'index.json'}
    for fact in roles.values():
        path = supply / 'p5/processes' / (fact['processIdentity'] + '.json')
        tag(load(path), anchor)
        assert load(path)['pid'] == fact['pid']
    assert load(supply / 'finished.json') == dict(rootAndIndexCheck=True, emptyLeaseIndex=True, syntheticTestOnly=True)
    return dict(roles=roles, emptyFinalIndex=True, originalP5FilesInvariant=True, finalFiles=len(final))


def wrong_schema(run_output, attempt):
    raw = (run_output / 'tests.stdout').read_text('utf-8-sig')
    assert raw.count('Application run failed') == 1
    failure = raw.split('Application run failed', 1)[1]
    wrong = load(attempt / 'private/wrong-schema-startup.json')
    assert wrong['sourceUnchanged'] is True and wrong['exception'] in failure
    prefix = 'Caused by: com.acrylic.outbound.recovery.RecoveryDatabaseAdmissionException: '
    causes = [line.strip() for line in failure.splitlines() if line.startswith(prefix)]
    assert len(causes) == 1
    gaps = causes[0].split('RECOVERY_DATABASE_ADMISSION_REFUSED:', 1)[1].split(',')
    assert set(gaps) == {'DATABASE_BINDING_MISSING', 'DATABASE_IDENTITY_MISSING', 'DURABLE_INTENT_SCHEMA',
                         'DURABLE_INTENT_BASELINE', 'NORMAL_ADMISSION_MISSING', 'PROJECTION_MISSING'}
    return dict(gaps=sorted(gaps), startupLogSha256=digest(run_output / 'tests.stdout'))


def main():
    parser = argparse.ArgumentParser()
    for name in ('attempt', 'run-output', 'compiled', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    proof = dict(inputs=inputs(args), conservation=conservation(args.attempt), exports=exports(args.attempt),
                 http=http(args.attempt), control=control(args.attempt), promotion=promotion(args.attempt),
                 p5=p5(args.attempt), wrongSchema=wrong_schema(args.run_output, args.attempt))
    save(args.output, proof)
    print(json.dumps(dict(status='PASS', tables=proof['conservation']['tables'], output=str(args.output))))


if __name__ == '__main__':
    main()
