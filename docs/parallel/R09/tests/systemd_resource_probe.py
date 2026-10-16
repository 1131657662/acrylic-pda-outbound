"""Transient user-unit evidence capture with cleanup restricted to the owned unit."""
from pathlib import Path
import json
import subprocess
import time
from uuid import uuid4

CGROUP = Path('/sys/fs/cgroup')
CG_FILES = ('cpu.max', 'cpu.stat', 'memory.high', 'memory.max', 'memory.current',
            'memory.events', 'memory.swap.max', 'pids.max', 'pids.current', 'pids.events', 'cgroup.procs')


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n')


def command(args, root):
    result = subprocess.run(args, capture_output=True, text=True, timeout=20)
    number = len(list(root.glob('command-*.json'))) + 1
    save(root / f'command-{number:03}.json', dict(args=args, exitCode=result.returncode,
                                               stdout=result.stdout, stderr=result.stderr))
    if result.returncode:
        raise RuntimeError(f'Command failed: {args[:3]}: {result.stderr.strip()}')
    return result.stdout.strip()


def until(predicate, seconds=15):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        value = predicate()
        if value:
            return value
        time.sleep(.05)
    raise RuntimeError('Expected bounded resource state did not arrive')


def properties(unit, root):
    names = 'MainPID,ControlGroup,ActiveState,SubState,Result,MemoryHigh,MemoryMax,CPUQuotaPerSecUSec,TasksMax,KillMode,SendSIGKILL'
    raw = command(['systemctl', '--user', 'show', unit, '--property=' + names], root)
    return dict(line.split('=', 1) for line in raw.splitlines())


def capture(unit, root):
    props = properties(unit, root)
    path = CGROUP / props['ControlGroup'].lstrip('/')
    assert str(path).startswith(str(CGROUP / 'user.slice/')) and path.is_dir()
    return {'properties': props, 'kernel': {name: (path / name).read_text() for name in CG_FILES}}


def launch(case, profile, mode):
    unit = 'acrylic-d15-resource-' + uuid4().hex + '.service'
    properties_args = [f'--property={key}={value}' for key, value in profile.items()]
    safety = ['--property=Restart=no', '--property=RuntimeMaxSec=45', '--property=TimeoutStopSec=2',
              '--property=OOMPolicy=continue', '--property=MemorySwapMax=0']
    load = Path(__file__).with_name('systemd_resource_load.py').resolve()
    argument = profile['TasksMax'] if mode == 'tasks' else byte_size(profile['MemoryMax'])
    args = ['systemd-run', '--user', '--service-type=exec', '--unit=' + unit,
            *properties_args, *safety, '/usr/bin/python3', str(load), mode, str(case), str(argument)]
    command(args, case)
    return unit


def byte_size(value):
    assert value[-1] in ('M', 'G') and value[:-1].isdigit()
    return int(value[:-1]) * (1024 ** (2 if value[-1] == 'M' else 3))


def stop(unit, case):
    assert unit.startswith('acrylic-d15-resource-') and unit.endswith('.service')
    before = properties(unit, case)
    group = before['ControlGroup']
    if group:
        assert group.startswith('/user.slice/') and group.endswith('/' + unit)
    path = CGROUP / group.lstrip('/') if group else case / 'absent-cgroup'
    pids = (path / 'cgroup.procs').read_text().split() if path.is_dir() else []
    ready = case / 'ready.json'
    if ready.exists():
        identity = json.loads(ready.read_text())
        pids = sorted(set(pids + [str(identity['pid'])] +
                          [str(pid) for pid in identity.get('children', [])]))
    try:
        if path.is_dir():
            save(case / 'before-stop.json', capture(unit, case))
        command(['journalctl', '--user', '--unit=' + unit, '--no-pager', '-o', 'short-iso'], case)
    finally:
        command(['systemctl', '--user', 'stop', unit], case)
    until(lambda: all(not Path('/proc', pid).exists() for pid in pids))
    after = properties(unit, case)
    save(case / 'stopped.json', {'before': before, 'after': after, 'pidsAbsent': pids})
    assert after['MainPID'] == '0' and after['ActiveState'] in ('inactive', 'failed')
