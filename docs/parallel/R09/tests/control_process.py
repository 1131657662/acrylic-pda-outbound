"""Subprocess-only crash and lock harness; no production fault switches."""
from pathlib import Path
import os
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'ops'))
from control_disk import read, locked
from control_format import canonical
from control_paths import ControlLocation
from control_store import ControlStore
from safety import OpsError


def execute():
    base = Path(sys.argv[1])
    config = read(base / 'process.json')
    location = ControlLocation(Path(config['root']), config['installation'], tuple(config['restore']))
    mode = sys.argv[2]
    if mode == 'lock':
        with locked(location.root):
            print('LOCKED', flush=True)
            sys.stdin.readline()
        return
    def fault(point):
        if point == mode:
            os._exit(73)
    if mode == 'race':
        print('READY', flush=True)
        sys.stdin.readline()
    value = read(base / sys.argv[3])
    if sys.argv[3] == 'init.json':
        result = ControlStore(location, fault).initialize(value)
        print(canonical(result).decode('utf-8'), flush=True)
        return
    trusted = read(base / 'checkpoint.json')
    result = ControlStore(location, fault).apply(value, trusted)
    print(canonical(result).decode('utf-8'), flush=True)


if __name__ == '__main__':
    try:
        execute()
    except OpsError as error:
        print(str(error), flush=True)
        sys.exit(2)
