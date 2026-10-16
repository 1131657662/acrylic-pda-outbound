"""Bounded synthetic resource loads; no application, network, or database access."""
from pathlib import Path
import json
import os
import subprocess
import sys
import threading
import time


def record(root, name, value):
    (root / name).write_text(json.dumps(value, indent=2) + '\n')


def cpu_child():
    deadline = time.monotonic() + 30
    value = 1
    while time.monotonic() < deadline:
        value = (value * 17 + 31) % 1000003


def cpu(root):
    children = [subprocess.Popen([sys.executable, __file__, 'cpu-child']) for _ in range(2)]
    record(root, 'ready.json', {'pid': os.getpid(), 'children': [p.pid for p in children]})
    time.sleep(35)


def tasks(root, limit):
    threading.stack_size(64 * 1024)
    hold = threading.Event()
    threads = []
    blocked = None
    for _ in range(limit + 1):
        try:
            thread = threading.Thread(target=hold.wait, args=(40,), daemon=True)
            thread.start()
            threads.append(thread)
        except RuntimeError as error:
            blocked = str(error)
            break
    record(root, 'ready.json', {'pid': os.getpid(), 'threads': len(threads), 'blocked': blocked})
    time.sleep(35)


def memory_child(root, maximum):
    Path('/proc/self/oom_score_adj').write_text('1000')
    blocks = []
    deadline = time.monotonic() + 35
    chunk = 4 * 1024 * 1024
    while len(blocks) * chunk <= maximum + 64 * 1024 * 1024:
        if time.monotonic() > deadline:
            raise RuntimeError('Bounded memory load timed out before kernel rejection')
        block = bytearray(chunk)
        block[::4096] = b'\x01' * (chunk // 4096)
        blocks.append(block)
        record(root, 'allocation.json', {'touchedBytes': len(blocks) * chunk})
    raise RuntimeError('MemoryMax failed to reject bounded synthetic allocation')


def memory(root, maximum):
    child = subprocess.Popen([sys.executable, __file__, 'memory-child', str(root), str(maximum)])
    record(root, 'ready.json', {'pid': os.getpid(), 'children': [child.pid]})
    result = child.wait(timeout=40)
    record(root, 'memory-result.json', {'allocatorExit': result, 'pid': child.pid})
    time.sleep(30)


def main():
    mode = sys.argv[1]
    if mode == 'cpu-child':
        cpu_child()
        return
    root = Path(sys.argv[2])
    if mode == 'cpu':
        cpu(root)
    elif mode == 'tasks':
        tasks(root, int(sys.argv[3]))
    elif mode == 'memory':
        memory(root, int(sys.argv[3]))
    elif mode == 'memory-child':
        memory_child(root, int(sys.argv[3]))
    else:
        raise ValueError('Unknown bounded load')


if __name__ == '__main__':
    main()
