"""Actual Linux cgroup containment checks, requiring a working isolated user manager."""
from pathlib import Path
import argparse
import json
import os
import subprocess
import time
from uuid import uuid4


def command(args):
    result = subprocess.run(args, capture_output=True, text=True, timeout=20)
    if result.returncode:
        raise RuntimeError(f'Failed {args[0:3]}: {result.stderr.strip()}')
    return result.stdout.strip()


def until(predicate, seconds=15):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(.05)
    raise RuntimeError('Expected OS process state did not arrive')


def properties(unit):
    raw = command(['systemctl', '--user', 'show', unit, '--property=MainPID,ControlGroup,KillMode,SendSIGKILL,ActiveState,SubState,Result'])
    return dict(line.split('=', 1) for line in raw.splitlines())


def no_processes(pids):
    return all(not Path(f'/proc/{pid}').exists() for pid in pids)


def exercise(root, mode):
    case = root / mode
    case.mkdir()
    unit = 'acrylic-r09-drain-' + uuid4().hex + '.service'
    harness = Path(__file__).with_name('systemd_process_tree.py').resolve()
    launch = ['systemd-run', '--user', '--unit=' + unit, '--service-type=exec',
              '--property=KillMode=control-group', '--property=SendSIGKILL=yes',
              '--property=TimeoutStopSec=2', '--property=Restart=no',
              '/usr/bin/python3', str(harness), 'owner', str(case)]
    (case / 'launch.json').write_text(json.dumps(launch) + '\n')
    command(launch)
    try:
        until(lambda: (case / 'ready').exists())
        verify_and_stop(case, unit, mode)
    finally:
        command(['systemctl', '--user', 'stop', unit])
        # Keep failed unit metadata and on-disk evidence; no reset-failed or file cleanup.


def verify_and_stop(case, unit, mode):
    pids = [int(value) for value in (case / 'ready').read_text().split()]
    before = properties(unit)
    assert before['KillMode'] == 'control-group' and before['SendSIGKILL'] == 'yes'
    assert int(before['MainPID']) == pids[0] and before['ControlGroup']
    membership = {str(pid): Path(f'/proc/{pid}/cgroup').read_text() for pid in pids}
    assert all(value.strip() == '0::' + before['ControlGroup'] for value in membership.values())
    started = time.monotonic()
    action = 'kill' if mode == 'main-death' else 'stop'
    args = ['systemctl', '--user', action]
    if action == 'kill':
        args += ['--kill-whom=main', '--signal=KILL']
    command([*args, unit])
    until(lambda: no_processes(pids))
    after = properties(unit)
    assert after['MainPID'] == '0' and after['ActiveState'] in ('failed', 'inactive')
    result = {'unit': unit, 'mode': mode, 'before': before, 'membership': membership,
              'after': after, 'pidsAbsent': pids, 'elapsedSeconds': time.monotonic() - started}
    (case / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'mode': mode, 'status': 'PASS', 'elapsedSeconds': result['elapsedSeconds']}), flush=True)


def verify_templates(project):
    for name in ('acrylic-outbound-test.service', 'acrylic-outbound-test-worker.service'):
        text = (project / 'deploy/test' / name).read_text()
        assert 'KillMode=control-group\n' in text and 'SendSIGKILL=yes\n' in text


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--evidence-root', type=Path, required=True)
    args = parser.parse_args()
    assert os.name == 'posix' and Path('/sys/fs/cgroup/cgroup.controllers').is_file()
    verify_templates(Path(__file__).resolve().parents[4])
    args.evidence_root.mkdir(parents=True, exist_ok=False)
    for mode in ('normal-stop', 'main-death'):
        exercise(args.evidence_root, mode)
    (args.evidence_root / 'summary.json').write_text(json.dumps({'tests': 2, 'passed': 2, 'skipped': 0,
        'scope': 'ISOLATED_USER_SYSTEMD_CGROUP', 'productionServiceTested': False}) + '\n')


if __name__ == '__main__':
    main()
