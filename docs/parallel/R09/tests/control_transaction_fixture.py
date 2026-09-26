"""Independent Python control originals for the explicit isolated JDBC acceptance test."""
from pathlib import Path
import sys
from control_factory import fixture, next_command, write
from control_store import ControlStore
from control_paths import ControlLocation


def create(root, restore_roots):
    if not restore_roots or any(not path.is_dir() for path in restore_roots):
        raise ValueError('Actual isolated database/storage restore roots required')
    root.mkdir(parents=True, exist_ok=False)
    base, _, initial = fixture(root)
    location = ControlLocation(base / 'authority', initial['installationId'], tuple(restore_roots))
    result = ControlStore(location).initialize(initial)
    write(base / 'checkpoint.json', result['checkpoint'])
    write(base / 'next.json', next_command(result))
    write(base / 'init.json', initial)
    write(base / 'process.json', {'root': str(location.root), 'installation': location.installation_id,
          'restore': [str(p) for p in location.restore_roots]})
    write(root / 'fixture.json', {'base': str(base), 'root': str(location.root),
          'installationId': location.installation_id, 'restoreRoots': [str(p) for p in location.restore_roots],
          'checkpoint': str(base / 'checkpoint.json')})
    print('CREATED', flush=True)


if __name__ == '__main__':
    create(Path(sys.argv[1]), [Path(value) for value in sys.argv[2:]])
