"""Offline consumer of one bound cross-instance restore, actual worker death and fresh API."""
import argparse
from pathlib import Path

from d15_combination_runtime import digest, save
from verify_d13_cross_instance_successor import copied_inputs, identities, conservation, refusal
from verify_d13_cross_instance_composition import credential
from verify_d13_successor_composition import inputs, conservation as runtime, control, promotion, wrong_schema
from verify_d13_restored_closed import exports, load
from verify_d13_restored_closed_p5 import options, tag, hashed, live, finish_p5
from d13_successor_fact_consume import rows
import uuid


def identity(home, target, allocation):
    assert load(home / 'cross-instance-target.json') == {**target, 'logBin': 1}
    secret = credential(Path(allocation['attempt']))
    roles = []
    for role in ('api', 'worker'):
        config = options(home, role)
        assert config['spring.datasource.url'] == (
            f"jdbc:mysql://127.0.0.1:{target['port']}/{target['schemaName']}"
            '?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC')
        assert config['spring.datasource.username'] == secret['user']
        assert config['spring.datasource.password'] == secret['password']
        assert config['outbound.worker.enabled'] == str(role == 'worker').lower()
        if role == 'worker':
            assert config['spring.main.web-application-type'] == 'none' and config['spring.profiles.active'] == 'worker'
        path = home / (role + '-database-identity.json')
        if path.exists():
            fact = load(path)
            assert fact['database'] == {**target, 'logBin': 1} and fact['role'] == role
            assert fact['pid'] == load(home / (role + '-ready.json'))['pid']
            roles.append(role)
    return roles


def p5(attempt):
    home = attempt / 'private/restored-bound-crash'; supply = attempt / 'private/restored-admission-p5'
    config = options(home, 'api'); worker = options(home, 'worker'); prefix = 'outbound.recovery.'
    for key in ('lease.root', 'lease.closed-authority', 'lease.identity-sha256', 'lease.marker-sha256',
                'effect-gate.anchor', 'root', 'checkpoint', 'installation-id'):
        assert config[prefix + key] == worker[prefix + key]
    for value in (config, worker):
        assert all(value[prefix + k] == 'true' for k in ('admission.enabled', 'lease.enabled',
                   'effect-gate.enabled', 'effect-gate.cross-process-enabled'))
        assert Path(value[prefix + 'lease.root']) == supply / 'p5'
    anchor = bytes.fromhex(config[prefix + 'effect-gate.anchor'])
    marker = load(supply / 'p5/lease-root.json')
    assert hashed(marker) == config[prefix + 'lease.marker-sha256']
    assert marker['bindingSha256'] == hashed({k: v for k, v in marker.items() if k != 'bindingSha256'})
    roles = {role: operation(home, role, (anchor, config[prefix + 'lease.identity-sha256'])) for role in ('api', 'worker')}
    cold = attempt / 'private/restored-closed-p5'
    roles['freshApi'] = live(cold, 'api', anchor, config[prefix + 'lease.identity-sha256'])
    assert len({fact['pid'] for fact in roles.values()}) == 3
    return finish_p5(supply, roles, anchor)


def operation(home, role, pins):
    anchor, identity_sha = pins
    fact = load(home / ('worker-crash-held.json' if role == 'worker' else 'api-p5-live.json'))
    ready = load(home / (role + '-ready.json'))
    lease, process, index = [fact[k] for k in ('lease', 'process', 'index')]
    tag(lease, anchor); tag(process, anchor); tag(index, anchor, 'indexTag')
    assert fact['pid'] == ready['pid'] == process['pid'] and fact['phase'] == 'SERVING'
    assert ready['role'] == role and ready['web'] == (role == 'api')
    assert lease['operationId'] in index['entries'] and lease['processSha256'] == hashed(process)
    assert lease['processIdentity'] == process['processIdentity'] and process['identitySha256'] == identity_sha
    assert lease['installationId'] == process['installationId'] == index['installationId']
    return dict(pid=fact['pid'], processIdentity=process['processIdentity'], operationId=lease['operationId'])


def orphan(attempt):
    home = attempt / 'private/restored-bound-crash'; supply = attempt / 'private/restored-admission-p5'
    held = load(home / 'worker-crash-held.json'); operation = held['lease']['operationId']
    before, after, successor = [load(supply / (label + '-p5.json')) for label in ('before-crash', 'after-crash', 'after-successor')]
    assert before['leases/' + operation + '.json']['sha256'] == hashed(held['lease'])
    assert {k: v for k, v in before.items() if k not in ('leases/' + operation + '.json', 'leases/index.json')} == {
        k: v for k, v in after.items() if k != 'leases/index.json'}
    assert all(successor[k] == v for k, v in after.items()) and len(successor) == len(after) + 1
    assert successor == {p.relative_to(supply / 'p5').as_posix(): dict(bytes=p.stat().st_size, sha256=digest(p))
                         for p in (supply / 'p5').rglob('*') if p.is_file()}
    assert load(home / 'owner-before.json')['lifecycleLockHeld'] is True
    assert load(home / 'owner-after.json')['lifecycleLockHeld'] is False
    exit = load(home / 'worker-crash-exit.json')
    assert exit['pid'] == held['pid'] and exit['actualExitCode'] == 71 and not exit['aliveAfter'] and not exit['normalShutdown']
    assert not (home / 'worker-closed.marker').exists() and (home / 'api-closed.marker').is_file()
    assert (home / 'api-live-accepted.marker').is_file()
    assert load(home / 'api-crash-refused.json') == dict(pid=load(home / 'api-ready.json')['pid'],
        admissionCode='RECOVERY_LEASE_FENCED', effectCode='RECOVERY_LEASE_FENCED', liveRoots=0)
    return dict(actualHaltExit=71, orphanRetiredOnly=True, osLifecycleLockReleased=True, freshProductApiStarted=True)


def http(attempt):
    result = {}
    for phase in ('pending', 'regenerated', 'stale'):
        statuses, codes = [], []
        for path in sorted((attempt / ('private/http-' + phase)).glob('*.http.json')):
            fact = load(path); body = path.with_name(path.name.replace('.http.json', '.body'))
            assert fact['status'] == fact['expected'] and digest(body) == fact['sha256']
            statuses.append(fact['status'])
            if fact['status'] in (409, 503): codes.append(load(body)['code'])
        assert statuses
        result[phase] = dict(statuses=statuses, codes=codes)
    assert result['stale']['statuses'] == [200] * 5 + [503] * 6
    assert result['stale']['codes'] == ['RECOVERY_LEASE_OUTSTANDING'] + ['RECOVERY_LEASE_FENCED'] * 5
    for code in ('SERVER_RECOVERY_EPOCH_MISMATCH', 'EXPORT_GENERATION_CHANGED', 'IDEMPOTENCY_CONFLICT'):
        assert result['regenerated']['codes'].count(code) >= 4
    return result


def pending(attempt):
    fact = load(attempt / 'stale-pending.json'); view = fact['view']
    assert view['status'] == 'PENDING' and view['attempts'] == 0 and not view['downloadable']
    assert fact['payload']['artifact'] is None
    final = load(attempt / 'private/restored-final.json')
    jobs = rows(final, 'background_job')
    assert len(jobs) == 5
    row = next(row for row in jobs if str(uuid.UUID(bytes=row['id'])) == fact['id'])
    assert row['status'] == b'PENDING' and row['attempt_count'] == b'0'
    assert row['lease_owner'] is None and row['lease_token'] is None and row['lease_until'] is None
    return dict(id=fact['id'], attempts=0, pendingAfterCrashRefusals=True)


def main():
    parser = argparse.ArgumentParser()
    for name in ('attempt', 'run-output', 'compiled', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    _, copied = copied_inputs(args); ids = identities(args); allocation = load(args.run_output / 'allocation.json')
    assert credential(args.attempt)['password'] != credential(Path(allocation['copy']['attempt']))['password']
    assert identity(args.attempt / 'private/restored-bound-crash', ids[1], allocation['copy']) == ['api', 'worker']
    assert identity(args.attempt / 'private/restored-closed-p5', ids[1], allocation['copy']) == ['api']
    private = args.attempt / 'private'
    assert load(private / 'source-trigger-facts.json') == load(private / 'target-trigger-final.json')
    proof = dict(inputs=inputs(args), copyInputs=len(copied), identities=ids, restore=conservation(args, ids),
        runtime=runtime(args.attempt), refusal=refusal(args.attempt), control=control(args.attempt),
        promotion=promotion(args.attempt), p5=p5(args.attempt), orphan=orphan(args.attempt),
        exports=exports(args.attempt), pending=pending(args.attempt), http=http(args.attempt), wrongSource=wrong_schema(args),
        actualUuidChange=True, syntheticAuthority=True, productP5OrApi=True)
    save(args.output, proof)
    print('PASS: bound cross-instance worker death, orphan retirement and fresh product API originals consumed')


if __name__ == '__main__':
    main()
