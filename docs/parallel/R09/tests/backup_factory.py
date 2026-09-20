"""Synthetic archive fixtures; no application accounts or real business data."""
from datetime import datetime, timezone
import gzip
from pathlib import Path
import tempfile
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'ops'))
from archive import pack
from backup import COMPONENTS
from lab import LAB_ROOT
from safety import inventory, secure_directory, sha, write_json


def test_directory():
    base = LAB_ROOT / 'tests'
    if not base.exists():
        secure_directory(base)
    return Path(tempfile.mkdtemp(prefix='case-', dir=base))


def seal(path):
    sums = ''.join(f'{sha(path / name)}  {name}\n' for name in sorted(COMPONENTS))
    (path / 'SHA256SUMS').write_text(sums, encoding='ascii')
    complete = {'format': 'r09-lab-v1', 'finished_at': datetime.now(timezone.utc).isoformat(),
                'sums_sha256': sha(path / 'SHA256SUMS')}
    import json
    (path / 'COMPLETE.json').write_text(json.dumps(complete), encoding='utf-8')


def backup_fixture(root):
    path = root / 'backups' / '20260918T000000Z-001'
    path.mkdir(parents=True)
    private = root / 'fixture'
    roots = {label: private / label for label in ('config', 'files')}
    for directory in roots.values():
        directory.mkdir(parents=True)
    for name in ('runtime.json', 'private.pk8', 'public.der'):
        (roots['config'] / name).write_bytes(b'synthetic non-secret fixture')
    (roots['files'] / 'evidence.txt').write_bytes(b'synthetic business evidence')
    with gzip.open(path / 'database.sql.gz', 'wb') as stream:
        stream.write(b'-- synthetic SQL')
    for name in ('database-facts.json', 'business.json', 'manifest.json'):
        write_json(path / name, {})
    (path / 'schema.sql').write_bytes(b'-- synthetic schema')
    trees = {label: inventory(directory) for label, directory in roots.items()}
    write_json(path / 'snapshot.json', {'format': 'r09-lab-v1',
        'captured_at': datetime.now(timezone.utc).isoformat(), 'trees': trees})
    pack(path / 'private-files-and-config.tar.gz', roots)
    seal(path)
    return path
