"""Owned synthetic control originals bound to the actual dump and pre-restore synthetic seed."""
import argparse
import hashlib
import json
from pathlib import Path
import re

from control_factory import fixture, next_command, write
from control_paths import ControlLocation
from control_store import ControlStore


def create(args):
    raw = args.source_input.read_bytes()
    supplied = json.loads(raw)
    source = supplied['source']
    assert supplied['syntheticAuthority'] is True and supplied['restoredSourceWasUnbound'] is True
    assert re.fullmatch('[a-f0-9]{64}', source['manifestSha256'])
    assert len(args.restore_roots) == 2 and all(path.is_dir() for path in args.restore_roots)
    args.root.mkdir(parents=True, exist_ok=False)
    base, _, initial = fixture(args.root)
    initial.update(manifestSha256=source['manifestSha256'],
                   snapshotId='owned-restored-' + source['manifestSha256'][:16],
                   affectedWarehouseIds=[source['identity']['warehouseId']],
                   evidenceSha256=hashlib.sha256(raw).hexdigest())
    location = ControlLocation(base / 'authority', initial['installationId'], tuple(args.restore_roots))
    result = ControlStore(location).initialize(initial)
    write(base / 'checkpoint.json', result['checkpoint'])
    write(base / 'next.json', next_command(result))
    write(base / 'init.json', initial)
    write(base / 'process.json', {'root': str(location.root), 'installation': location.installation_id,
          'restore': [str(path) for path in location.restore_roots]})
    write(args.root / 'fixture.json', {'base': str(base), 'root': str(location.root),
          'installationId': location.installation_id, 'restoreRoots': [str(path) for path in location.restore_roots],
          'checkpoint': str(base / 'checkpoint.json')})
    print('CREATED', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('root', type=Path)
    parser.add_argument('restore_roots', nargs='+', type=Path)
    parser.add_argument('--source-input', required=True, type=Path)
    create(parser.parse_args())
