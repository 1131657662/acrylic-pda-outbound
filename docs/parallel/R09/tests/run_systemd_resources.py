"""Exercise actual template cgroup limits in synthetic, isolated Linux user units."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import sys
import time

from systemd_resource_probe import byte_size, capture, command, launch, save, stop, until

PROJECT = Path(__file__).resolve().parents[4]
TEMPLATES = ('deploy/test/acrylic-outbound-test.service',
             'deploy/test/acrylic-outbound-test-worker.service',
             'docs/parallel/R09/config/acrylic-outbound-test-ops-check.service')
LIMITS = ('MemoryHigh', 'MemoryMax', 'CPUQuota', 'TasksMax')


def profile(path):
    lines = dict(line.split('=', 1) for line in path.read_text().splitlines()
                 if '=' in line and not line.startswith('#'))
    result = {key: lines[key] for key in LIMITS}
    result.update(KillMode=lines.get('KillMode', 'control-group'),
                  SendSIGKILL=lines.get('SendSIGKILL', 'yes'))
    return result


def counters(text):
    return {key: int(value) for key, value in (line.split() for line in text.splitlines())}


def verify_limits(sample, selected, ready):
    kernel = sample['kernel']
    props = sample['properties']
    assert int(kernel['memory.high']) == byte_size(selected['MemoryHigh'])
    assert int(kernel['memory.max']) == byte_size(selected['MemoryMax'])
    assert int(kernel['pids.max']) == int(selected['TasksMax'])
    quota, period = map(int, kernel['cpu.max'].split())
    assert quota / period == int(selected['CPUQuota'].removesuffix('%')) / 100
    assert props['KillMode'] == 'control-group' and props['SendSIGKILL'] == 'yes'
    assert int(props['MainPID']) == ready['pid']
    assert kernel['memory.swap.max'].strip() == '0'
    membership = Path('/proc', str(ready['pid']), 'cgroup').read_text().strip()
    assert membership == '0::' + props['ControlGroup']


def verify_cpu(unit, case, selected):
    before = capture(unit, case)
    started = time.monotonic()
    time.sleep(6)
    after = capture(unit, case)
    elapsed = time.monotonic() - started
    old, new = (counters(sample['kernel']['cpu.stat']) for sample in (before, after))
    used = (new['usage_usec'] - old['usage_usec']) / 1000000
    ceiling = int(selected['CPUQuota'].removesuffix('%')) / 100
    save(case / 'cpu-samples.json', dict(before=before, after=after, elapsed=elapsed, cpuSeconds=used))
    assert new['nr_throttled'] > old['nr_throttled']
    assert new['throttled_usec'] > old['throttled_usec']
    assert .1 * ceiling < used / elapsed < ceiling * 1.4


def verify_tasks(unit, case, ready):
    sample = capture(unit, case)
    save(case / 'tasks-sample.json', sample)
    maximum = int(sample['kernel']['pids.max'])
    assert ready['blocked'] == "can't start new thread"
    assert ready['threads'] == maximum - 1
    assert int(sample['kernel']['pids.current']) == maximum
    assert counters(sample['kernel']['pids.events'])['max'] > 0


def memory_high(unit, case):
    def high_observed():
        sample = capture(unit, case)
        save(case / 'memory-last.json', sample)
        return sample if counters(sample['kernel']['memory.events'])['high'] > 0 else None

    sample = until(high_observed)
    save(case / 'memory-high.json', sample)
    return sample


def verify_memory(unit, case, selected):
    memory_high(unit, case)
    command(['systemctl', '--user', 'set-property', '--runtime', unit,
             'MemoryHigh=' + selected['MemoryMax']], case)
    adjusted = capture(unit, case)
    save(case / 'memory-max-stage.json', adjusted)
    assert int(adjusted['kernel']['memory.high']) == byte_size(selected['MemoryMax'])
    assert int(adjusted['kernel']['memory.max']) == byte_size(selected['MemoryMax'])

    def oom_observed():
        sample = capture(unit, case)
        save(case / 'memory-last.json', sample)
        events = counters(sample['kernel']['memory.events'])
        return sample if events['oom_kill'] > 0 else None

    sample = until(oom_observed, seconds=42)
    save(case / 'memory-sample.json', sample)
    events = counters(sample['kernel']['memory.events'])
    assert events['high'] > 0 and events['max'] > 0 and events['oom_kill'] > 0
    until(lambda: (case / 'memory-result.json').exists())
    assert json.loads((case / 'memory-result.json').read_text())['allocatorExit'] == -9
    settled = capture(unit, case)
    save(case / 'memory-settled.json', settled)
    assert int(settled['kernel']['memory.current']) < int(settled['kernel']['memory.max'])


def exercise(case, selected, mode):
    case.mkdir()
    unit = launch(case, selected, mode)
    save(case / 'identity.json', {'unit': unit, 'profile': selected, 'scope': 'SYNTHETIC_USER_UNIT'})
    try:
        until(lambda: (case / 'ready.json').exists())
        ready = json.loads((case / 'ready.json').read_text())
        sample = capture(unit, case)
        save(case / 'initial.json', sample)
        verify_limits(sample, selected, ready)
        if mode == 'cpu':
            verify_cpu(unit, case, selected)
        elif mode == 'tasks':
            verify_tasks(unit, case, ready)
        else:
            verify_memory(unit, case, selected)
    except Exception as error:
        save(case / 'failure.json', {'type': type(error).__name__, 'message': str(error)})
        raise
    finally:
        stop(unit, case)
    save(case / 'result.json', {'status': 'PASS', 'mode': mode, 'unit': unit})
    print(json.dumps({'case': case.name, 'status': 'PASS'}), flush=True)


def record_inputs(root):
    names = ('run_systemd_resources.py', 'systemd_resource_probe.py', 'systemd_resource_load.py')
    sources = {name: Path(__file__).with_name(name).read_text() for name in names}
    save(root / 'runner-input.json', dict(sources=sources, argv=sys.argv,
                                        python=sys.version, kernel=Path('/proc/version').read_text()))
    command(['systemd', '--version'], root)
    hashes = {name: hashlib.sha256((PROJECT / name).read_bytes()).hexdigest() for name in TEMPLATES}
    save(root / 'templates.json', hashes)
    return hashes


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--evidence-root', required=True, type=Path)
    parser.add_argument('--template-index', type=int, choices=(1, 2, 3))
    parser.add_argument('--modes', nargs='+', choices=('cpu', 'tasks', 'memory'),
                        default=('cpu', 'tasks', 'memory'))
    args = parser.parse_args()
    root = args.evidence_root.resolve()
    assert os.name == 'posix' and root.is_relative_to(PROJECT / '.local')
    root.mkdir(parents=True, exist_ok=False)
    hashes = record_inputs(root)
    selected_templates = list(enumerate(TEMPLATES))
    if args.template_index is not None:
        selected_templates = [selected_templates[args.template_index - 1]]
    for index, name in selected_templates:
        selected = profile(PROJECT / name)
        for mode in args.modes:
            exercise(root / f'{index + 1}-{mode}', selected, mode)
    save(root / 'summary.json', dict(tests=len(selected_templates) * len(args.modes),
                                     passed=len(selected_templates) * len(args.modes), skipped=0,
                                     scope='SYNTHETIC_USER_SYSTEMD',
                                     productionServiceTested=False, templates=hashes))


if __name__ == '__main__':
    main()
