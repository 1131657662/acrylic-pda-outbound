"""Original request fixtures and retained on-disk evidence for control tests."""
from pathlib import Path
import sys
import tempfile
from uuid import uuid4
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'ops'))
from control_format import canonical
from control_paths import ControlLocation
from control_store import ControlStore

TEST_ROOT = Path(__file__).resolve().parents[4] / '.local' / 'r09' / 'control-tests'


def fixture(test_root=TEST_ROOT):
    test_root.mkdir(parents=True, exist_ok=True)
    base = Path(tempfile.mkdtemp(prefix='case-', dir=test_root))
    location = ControlLocation(base / 'authority', str(uuid4()), (base / 'business',))
    initial = {'formatVersion': 1, 'installationId': location.installation_id,
               'commandId': str(uuid4()), 'expectedRevision': '0', 'phase': 'QUIESCING',
               'epochId': str(uuid4()), 'recoverySessionId': str(uuid4()),
               'snapshotId': 'synthetic-snapshot', 'manifestSha256': 'a' * 64,
               'affectedWarehouseIds': ['W1', 'W2'], 'possibleLossFrom': '2026-09-20T00:00:00Z',
               'possibleLossThrough': '2026-09-21T00:00:00Z', 'evidenceSha256': 'b' * 64,
               'actorId': 'synthetic-operator', 'requestedAt': '2026-09-22T00:00:00Z'}
    return base, location, initial


def next_command(result, phase='RECOVERING'):
    previous = result['record']
    return {**previous['command'], 'commandId': str(uuid4()),
            'expectedRevision': previous['revision'], 'phase': phase,
            'epochId': str(uuid4()) if phase == 'RECOVERING' else previous['command']['epochId']}


def initialized():
    base, location, initial = fixture()
    result = ControlStore(location).initialize(initial)
    return base, location, initial, result


def write(path, value):
    path.write_bytes(canonical(value))
    return path
