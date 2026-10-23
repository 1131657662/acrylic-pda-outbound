"""Offline consumption of one actual cross-instance successor/API/worker/P5/file run."""
import argparse
from pathlib import Path

from d15_combination_runtime import save
from verify_d13_cross_instance_successor import copied_inputs, identities, conservation, refusal
from verify_d13_successor_composition import (
    inputs, conservation as runtime_conservation, control, promotion, wrong_schema,
)
from verify_d13_restored_closed import exports, http, load
from verify_d13_restored_closed_p5 import options, p5


def credential(attempt):
    values = dict(line.split('=', 1) for line in
                  (attempt / 'private/client.cnf').read_text('utf-8').splitlines() if '=' in line)
    assert values['host'] == '127.0.0.1' and values['protocol'] == 'tcp'
    return values


def processes(args, identity, p5_fact):
    allocation = load(args.run_output / 'allocation.json')
    home = args.attempt / 'private/restored-closed-p5'
    target = identity[1]; copied = allocation['copy']
    expected = {**target, 'logBin': 1}
    assert load(home / 'cross-instance-target.json') == expected
    copied_secret = credential(Path(copied['attempt']))
    assert copied_secret['password'] != credential(args.attempt)['password']
    facts = {}
    for role in ('api', 'worker'):
        values = options(home, role)
        assert values['spring.datasource.url'] == (
            f"jdbc:mysql://127.0.0.1:{copied['port']}/{target['schemaName']}"
            '?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC')
        assert values['spring.datasource.username'] == copied_secret['user']
        assert values['spring.datasource.password'] == copied_secret['password']
        assert Path(values['outbound.storage']).resolve() == (args.attempt / 'restored/storage').resolve()
        assert int(values['server.port']) == allocation['http']
        fact = load(home / (role + '-database-identity.json'))
        assert fact['role'] == role and fact['database'] == expected
        assert fact['pid'] == p5_fact['roles'][role]['pid']
        facts[role] = fact
    assert facts['api']['pid'] != facts['worker']['pid']
    return dict(targetIdentityVerifiedByBothChildren=True, differentSyntheticCredentials=True,
                roles=facts, actualTargetUuid=target['uuid'])


def main():
    parser = argparse.ArgumentParser()
    for name in ('attempt', 'run-output', 'compiled', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    _, manifest = copied_inputs(args)
    identity = identities(args)
    live_p5 = p5(args.attempt)
    private = args.attempt / 'private'
    assert load(private / 'source-trigger-facts.json') == load(private / 'target-trigger-final.json')
    proof = dict(inputs=inputs(args), copyInputs=len(manifest), identities=identity,
        restore=conservation(args, identity), runtime=runtime_conservation(args.attempt),
        refusal=refusal(args.attempt), control=control(args.attempt), promotion=promotion(args.attempt),
        processes=processes(args, identity, live_p5), p5=live_p5,
        exports=exports(args.attempt), http=http(args.attempt), wrongSource=wrong_schema(args),
        actualUuidChange=True, syntheticAuthority=True, productP5OrApi=True)
    save(args.output, proof)
    print('PASS: bound cross-instance successor/API/separate worker/P5/file originals consumed')


if __name__ == '__main__':
    main()
