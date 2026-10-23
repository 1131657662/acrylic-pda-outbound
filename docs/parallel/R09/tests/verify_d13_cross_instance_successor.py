"""Independently consume dual-server identities, full restored facts and successor originals."""
import argparse
from pathlib import Path
import uuid

from d15_combination_runtime import digest, save
from d13_successor_fact_consume import rows
from verify_d13_restored_closed import load
from verify_d13_successor_composition import control, inputs, registration


def copied_inputs(args):
    allocation = load(args.run_output / 'allocation.json')['copy']
    target = Path(allocation['attempt'])
    manifest = load(args.run_output / 'copy-output-hashes.json')
    for name, fact in manifest.items():
        path = target / name
        assert digest(path) == fact['sha256'] and path.stat().st_size == fact['bytes']
    assert load(target / 'mysql-stopped.json')['exitCode'] == 0
    assert (target / 'identity-before.stdout').read_bytes() == (target / 'identity-final.stdout').read_bytes()
    assert (target / 'test-completed.marker').is_file()
    return target, manifest


def identities(args):
    allocation = load(args.run_output / 'allocation.json')
    private = args.attempt / 'private'
    source, target = [load(private / (name + '-identity.json')) for name in ('source', 'target')]
    assert source['uuid'] != target['uuid'] and source['port'] != target['port']
    assert Path(source['datadir']).resolve() != Path(target['datadir']).resolve()
    for fact, expected in ((source, allocation), (target, allocation['copy'])):
        home = Path(expected['attempt'])
        raw = (home / 'identity-before.stdout').read_text('utf-8').strip().split('\t')
        assert raw[5] == fact['uuid'] and int(raw[0]) == fact['port'] == expected['port']
        assert Path(fact['datadir']).resolve() == (home / 'data').resolve()
        assert fact['userName'] == raw[3] == 'e2a_cross_20260925_' + str(expected['run']) + '@127.0.0.1'
    assert source['schemaName'] == 'acrylic_e2a_cross_20260925_' + str(allocation['run'])
    assert target['schemaName'] == 'acrylic_e2a_cross_20260925_' + str(allocation['copy']['run']) + '_restore'
    roots = load(args.attempt / 'successor-control/fixture.json')['restoreRoots']
    expected = [args.attempt / 'data', args.attempt / 'storage',
        Path(allocation['copy']['attempt']) / 'data', args.attempt / 'restored/storage']
    assert roots == sorted(roots) and {Path(p).resolve() for p in roots} == {p.resolve() for p in expected}
    return source, target


def conservation(args, identity):
    private = args.attempt / 'private'
    before, after = [load(private / name) for name in ('restored-before.json', 'restored-admitted.json')]
    assert before == load(private / 'archive-snapshot-facts.json') == load(private / 'source-final.json')
    changed = registration(before, after)
    assert all(before[key] == after[key] for key in ('schema', 'files', 'flywayHistory'))
    assert before['files'] and any(fact['rows'] for fact in before['tables'].values())
    for name, fact in after['files'].items():
        path = args.attempt / 'restored/storage' / name
        assert digest(path) == fact['sha256'] and path.stat().st_size == fact['bytes']
    original = rows(after, 'recovery_installation_binding')[0]
    successor = rows(after, 'recovery_successor_binding')[0]
    assert str(uuid.UUID(bytes=original['mysql_server_uuid'])) == identity[0]['uuid']
    assert str(uuid.UUID(bytes=successor['mysql_server_uuid'])) == identity[1]['uuid']
    triggers = load(private / 'source-trigger-facts.json')
    assert triggers and triggers == load(private / 'target-trigger-facts.json')
    return dict(tables=len(before['tables']), files=len(before['files']), triggers=len(triggers),
        registrationChanged=changed, sourceInvariant=True, originalBindingInvariant=True)


def refusal(attempt):
    before = load(attempt / 'private/copied-before-admission.json')
    after = load(attempt / 'private/source-after-admission.json')
    assert not before['admitted'] and 'DATABASE_SERVER_UUID' in before['gaps']
    assert not after['admitted'] and after['gaps'] == ['DATABASE_EVIDENCE_UNAVAILABLE']
    return dict(before=before['gaps'], sourceAfter=after['gaps'])


def main():
    parser = argparse.ArgumentParser()
    for name in ('attempt', 'run-output', 'compiled', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    _, manifest = copied_inputs(args)
    identity = identities(args)
    proof = dict(inputs=inputs(args), copyInputs=len(manifest), identities=identity,
        conservation=conservation(args, identity), control=control(args.attempt), refusal=refusal(args.attempt),
        actualUuidChange=True, syntheticAuthority=True, productP5OrApi=False)
    save(args.output, proof)
    print('PASS: bound nonempty cross-instance successor originals consumed')


if __name__ == '__main__':
    main()
