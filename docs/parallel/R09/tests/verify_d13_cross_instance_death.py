"""Offline evidence consumer for real successor deaths on bound cross-instance product inputs."""
import argparse
from pathlib import Path

from d15_combination_runtime import digest, save
from verify_d13_cross_instance_composition import processes
from verify_d13_cross_instance_successor import copied_inputs, identities, conservation, refusal
from verify_d13_successor_composition import inputs, conservation as runtime, control, promotion, wrong_schema
from verify_d13_restored_closed import exports, http, load
from verify_d13_restored_closed_p5 import options, p5

POINTS = {'intent_appended': 'CONTROL_INTENT_INCOMPLETE',
          'intent_head_staged': 'CONTROL_UNKNOWN_ARTIFACT',
          'intent_head_committed': 'CONTROL_CLOSING_INTENT',
          'history_appended': 'CONTROL_COMMAND_INCOMPLETE',
          'pointer_staged': 'CONTROL_COMMAND_INCOMPLETE',
          'pointer_committed': 'RECOVERY_DATABASE_ADMISSION_REFUSED'}


def snapshots(home):
    result = {}
    for name in ('before', 'interrupted', 'before-conflict', 'after-conflict', 'after-observers', 'resumed', 'replayed'):
        expected = load(home / (name + '.json'))
        actual = {p.relative_to(home / name).as_posix(): digest(p)
                  for p in (home / name).rglob('*') if p.is_file()}
        assert actual == expected
        result[name] = expected
    assert result['interrupted'] == result['before-conflict'] == result['after-conflict'] == result['after-observers']
    assert result['resumed'] == result['replayed']
    for name, sha in result['before'].items():
        if name not in ('current.json', 'admission.json'):
            assert result['resumed'][name] == sha
    return result


def executions(attempt, point):
    home = attempt / 'private/successor-death'
    base = Path(load(attempt / 'successor-control/fixture.json')['base'])
    executions = {}
    for label, mode, request, expected in (
            ('death', point, 'death-original.json', 73), ('conflict', 'apply', 'death-conflict.json', 2),
            ('resume', 'apply', 'death-original.json', 0), ('replay', 'apply', 'death-original.json', 0)):
        command = load(home / (label + '-command.json'))
        assert Path(command[1]).name == 'control_process.py' and command[2:] == [str(base), mode, request]
        result = load(home / (label + '-exit.json'))
        assert result['exitCode'] == expected and result['pid'] > 0 and result['aliveAfter'] is False
        assert (home / (label + '.stderr')).read_bytes() == b''
        executions[label] = result
    assert (home / 'death.stdout').read_bytes() == b''
    assert (home / 'conflict.stdout').read_text('utf-8').strip() in (
        'CONTROL_INTENT_CONFLICT', 'CONTROL_CAS_CONFLICT', 'CONTROL_COMMAND_INCOMPLETE')
    assert (home / 'resume.stdout').read_bytes() == (home / 'replay.stdout').read_bytes()
    original = load(home / 'command.json')
    assert original == load(base / 'death-original.json')
    alternate = load(base / 'death-conflict.json')
    assert original['commandId'] != alternate['commandId']
    assert {k: v for k, v in original.items() if k != 'commandId'} == {
        k: v for k, v in alternate.items() if k != 'commandId'}
    return executions


def pending_processes(args, target, point):
    home = args.attempt / 'private/successor-pending'
    expected = {**target, 'logBin': 1}
    assert load(home / 'cross-instance-target.json') == expected
    facts = {}
    for role in ('api', 'worker'):
        fact = load(home / (role + '-database-identity.json'))
        result = load(home / (role + '-refused.json'))
        assert fact['database'] == expected and fact['role'] == role
        assert result['role'] == role and result['exitCode'] == 1 and result['pid'] == fact['pid']
        assert not (home / (role + '-unexpected-ready.marker')).exists()
        values = options(home, role)
        assert values['spring.datasource.url'].startswith(f"jdbc:mysql://127.0.0.1:{target['port']}/{target['schemaName']}?")
        assert values['spring.datasource.username'] + '@127.0.0.1' == target['userName']
        assert values['outbound.worker.enabled'] == str(role == 'worker').lower()
        if role == 'worker':
            assert values['spring.main.web-application-type'] == 'none' and values['spring.profiles.active'] == 'worker'
        raw = (home / (role + '.stdout')).read_text('utf-8') + (home / (role + '.stderr')).read_text('utf-8')
        assert POINTS[point] in raw and 'Application run failed' in raw
        facts[role] = dict(pid=fact['pid'], exitCode=1, code=POINTS[point], targetIdentity=expected)
    assert facts['api']['pid'] != facts['worker']['pid']
    return facts


def death(args, identity):
    private = args.attempt / 'private'; home = private / 'successor-death'
    point = load(args.run_output / 'allocation.json')['deathPoint']
    assert point in POINTS
    assert load(home / 'result.json') == dict(point=point, deathExitCode=73, conflictExitCode=2,
        originalResumeExitCode=0, replayExitCode=0, originalsPreserved=True)
    retained = snapshots(home); executed = executions(args.attempt, point)
    assert load(private / 'death-database-before.json') == load(private / 'death-database-after.json') == load(private / 'restored-before.json')
    denied = load(private / 'death-runtime-admission.json')
    assert denied['admitted'] is False and denied['ordinaryWritesAllowed'] is False and denied['localDeletionAllowed'] is False
    observed = load(home / 'reader.json'); assert observed['point'] == point
    if point == 'intent_head_committed':
        assert observed['readerRevision'] == 4 and observed['writerCode'] == POINTS[point]
    elif point == 'pointer_committed':
        assert observed['readerRevision'] == 5
    else:
        assert observed['readerCode'] == POINTS[point]
    records = sorted((home / 'resumed/history').glob('*.json'))
    assert len(records) == 5 and load(records[-1])['command'] == load(home / 'command.json')
    return dict(point=point, executions=executed, snapshots={name: len(value) for name, value in retained.items()},
        pendingProcesses=pending_processes(args, identity[1], point), databaseAndControlUnchangedByObservers=True,
        originalCommandOnly=True, replayByteIdentical=True, reader=observed)


def main():
    parser = argparse.ArgumentParser()
    for name in ('attempt', 'run-output', 'compiled', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    _, copied = copied_inputs(args); identity = identities(args); live_p5 = p5(args.attempt)
    private = args.attempt / 'private'
    assert load(private / 'source-trigger-facts.json') == load(private / 'target-trigger-final.json')
    proof = dict(inputs=inputs(args), copyInputs=len(copied), identities=identity, death=death(args, identity),
        restore=conservation(args, identity), runtime=runtime(args.attempt), refusal=refusal(args.attempt),
        control=control(args.attempt), promotion=promotion(args.attempt), p5=live_p5,
        processes=processes(args, identity, live_p5), exports=exports(args.attempt), http=http(args.attempt),
        wrongSource=wrong_schema(args), actualUuidChange=True, syntheticAuthority=True, productP5OrApi=True)
    save(args.output, proof)
    print('PASS: actual cross-instance successor death/pending/original resume/API/worker/P5/files')


if __name__ == '__main__':
    main()
