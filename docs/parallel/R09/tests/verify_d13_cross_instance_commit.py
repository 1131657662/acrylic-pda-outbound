"""Consume bound cross-instance real product commit with synthetic lost acknowledgement and persistent UNKNOWN originals."""
import argparse
from pathlib import Path

from d15_combination_runtime import digest, save
from verify_d13_cross_instance_successor import copied_inputs, identities, conservation, refusal
from verify_d13_cross_instance_composition import credential
from verify_d13_cross_instance_worker_crash import identity
from d13_commit_visibility import commit, http
from verify_d13_successor_composition import inputs, conservation as runtime, control, promotion, wrong_schema
from verify_d13_restored_closed import exports, load
from verify_d13_restored_closed_p5 import options, tag, hashed


def operation(home, role, pins):
    anchor, identity_sha = pins
    name = 'worker-commit-held.json' if role == 'worker' else 'api-p5-live.json'
    fact = load(home / name); ready = load(home / (role + '-ready.json'))
    lease, process, index = [fact[k] for k in ('lease', 'process', 'index')]
    tag(lease, anchor); tag(process, anchor); tag(index, anchor, 'indexTag')
    assert fact['pid'] == ready['pid'] == process['pid'] and fact['phase'] == 'SERVING'
    assert ready['role'] == role and ready['web'] == (role == 'api')
    assert lease['operationId'] in index['entries'] and lease['processSha256'] == hashed(process)
    assert lease['processIdentity'] == process['processIdentity'] and process['identitySha256'] == identity_sha
    assert lease['installationId'] == process['installationId'] == index['installationId']
    return dict(pid=fact['pid'], processIdentity=process['processIdentity'], operationId=lease['operationId'])


def p5(attempt):
    home = attempt / 'private/restored-bound-commit'; supply = attempt / 'private/restored-admission-p5'
    config, worker = [options(home, role) for role in ('api', 'worker')]; prefix = 'outbound.recovery.'
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
    assert roles['api']['pid'] != roles['worker']['pid']
    initial = load(supply / 'p5-before.json')
    final = load(supply / 'final-unknown-p5.json')
    assert all(final[name]['sha256'] == sha for name, sha in initial.items())
    assert final == {p.relative_to(supply / 'p5').as_posix(): dict(bytes=p.stat().st_size, sha256=digest(p))
                     for p in (supply / 'p5').rglob('*') if p.is_file()}
    index = load(supply / 'p5/leases/index.json'); tag(index, anchor, 'indexTag')
    assert index['entries'] == []
    for fact in roles.values():
        process = supply / 'p5/processes' / (fact['processIdentity'] + '.json')
        tag(load(process), anchor); assert load(process)['pid'] == fact['pid']
    return dict(roles=roles, emptyFinalIndex=True, unknownStillBlocks=True, originalP5FilesInvariant=True)


def unknown(attempt):
    home = attempt / 'private/restored-bound-commit'; supply = attempt / 'private/restored-admission-p5'
    held = load(home / 'worker-commit-held.json'); operation_id = held['lease']['operationId']
    lease = 'leases/' + operation_id + '.json'; audit = 'leases/' + operation_id + '.unknown.json'
    before, failed, observed, successor, final = [load(supply / (name + '-p5.json')) for name in
        ('before-commit', 'after-failure', 'after-observation', 'after-successor', 'final-unknown')]
    assert before[lease]['sha256'] == hashed(held['lease'])
    assert {k: v for k, v in failed.items() if k != audit} == before and len(failed) == len(before) + 1
    assert {k: v for k, v in failed.items() if k not in (lease, 'leases/index.json')} == {
        k: v for k, v in observed.items() if k != 'leases/index.json'}
    assert observed == successor == final and audit in final and lease not in final
    raw = home / 'unknown-original.json'; assert digest(raw) == failed[audit]['sha256'] == final[audit]['sha256']
    value = load(raw)
    assert set(value) == {'operationId', 'processIdentity', 'event', 'at'}
    assert value['operationId'] == operation_id and value['processIdentity'] == held['lease']['processIdentity']
    assert value['event'] == 'RECOVERY_LEASE_FENCED'
    assert {p.name for p in (supply / 'p5/leases').iterdir()} == {'index.json', operation_id + '.unknown.json'}
    return dict(unknownPreserved=True, postFailureOriginalLeaseIndexProcessInvariant=True,
                onlyOrphanRetired=True, freshStartupNoP5Mutation=True, auditSha256=digest(raw))


def fresh(attempt):
    home = attempt / 'private/restored-closed-p5'; previous = attempt / 'private/restored-bound-commit'
    result = load(home / 'api-refused.json')
    assert result['actualExitCode'] == 1 and not result['aliveAfter'] and result['startupRefused'] is True
    assert result['code'] == 'RECOVERY_LEASE_OUTSTANDING'
    assert result['pid'] not in [load(previous / (role + '-ready.json'))['pid'] for role in ('api', 'worker')]
    actual = load(home / 'api-database-identity.json'); assert actual['pid'] == result['pid'] and actual['role'] == 'api'
    assert options(home, 'api') == options(previous, 'api')
    assert not (home / 'api-ready.json').exists() and not (home / 'api-closed.marker').exists()
    raw = (home / 'api.stdout').read_text('utf-8') + (home / 'api.stderr').read_text('utf-8')
    assert 'Application run failed' in raw and 'RECOVERY_LEASE_OUTSTANDING' in raw
    return dict(actualFreshProductApiRefused=True, noReadyOrNormalShutdown=True, pid=result['pid'])


def main():
    parser = argparse.ArgumentParser()
    for name in ('attempt', 'run-output', 'compiled', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    _, copied = copied_inputs(args); ids = identities(args); allocation = load(args.run_output / 'allocation.json')
    assert credential(args.attempt)['password'] != credential(Path(allocation['copy']['attempt']))['password']
    assert identity(args.attempt / 'private/restored-bound-commit', ids[1], allocation['copy']) == ['api', 'worker']
    assert load(args.attempt / 'private/restored-closed-p5/api-database-identity.json')['database'] == {**ids[1], 'logBin': 1}
    private = args.attempt / 'private'
    assert load(private / 'source-trigger-facts.json') == load(private / 'target-trigger-final.json')
    proof = dict(inputs=inputs(args), copyInputs=len(copied), identities=ids, restore=conservation(args, ids),
        runtime=runtime(args.attempt), refusal=refusal(args.attempt), control=control(args.attempt),
        promotion=promotion(args.attempt), p5=p5(args.attempt), unknown=unknown(args.attempt),
        commit=commit(args.attempt), fresh=fresh(args.attempt), exports=exports(args.attempt),
        http=http(args.attempt), wrongSource=wrong_schema(args),
        actualUuidChange=True, syntheticAuthority=True, productP5OrApi=True)
    save(args.output, proof)
    print('PASS: bound cross-instance real product commit with synthetic lost acknowledgement, UNKNOWN and fresh product API refusal consumed')


if __name__ == '__main__':
    main()
