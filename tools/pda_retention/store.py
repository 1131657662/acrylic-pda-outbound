"""New local synthetic copies and complete SQLite fact snapshots."""
import os
from contextlib import closing
from pathlib import Path
import sqlite3

from .codec import canonical, digest, require

ROOT = Path(__file__).resolve().parents[2]
LOCAL = ROOT / '.local'
MAX_BYTES = 128 * 1024 * 1024


def location(path):
    path = Path(path).absolute()
    require('..' not in path.parts, 'Parent traversal is forbidden')
    require(path.is_relative_to(LOCAL), 'Only project .local synthetic copies are supported')
    for part in [path, *path.parents]:
        if part == ROOT:
            break
        if part.exists():
            attrs = part.lstat()
            require(not part.is_symlink() and not (getattr(attrs, 'st_file_attributes', 0) & 1024),
                    'Links and reparse points are forbidden')
            if part.is_file():
                require(attrs.st_nlink == 1, 'Hard-linked input is forbidden')
    require(path.resolve().is_relative_to(LOCAL.resolve()), 'Resolved path escaped .local')
    return path


def read(path):
    path = location(path)
    require(path.is_file() and path.stat().st_size <= MAX_BYTES, 'Missing or oversized input')
    return path.read_bytes()


def write_new(path, value):
    path = location(path)
    raw = canonical(value)
    with path.open('xb') as output:
        output.write(raw)
        output.flush()
        os.fsync(output.fileno())
    return digest(raw)


def connect(path, writable=False):
    path = location(path)
    require(path.is_file(), 'Database must already exist')
    for suffix in ('-wal', '-shm', '-journal'):
        if writable and suffix == '-journal':
            continue  # SQLite itself serializes concurrent writes to the known run copy.
        require(not Path(str(path) + suffix).exists(), 'Stopped single-file input required')
    db = sqlite3.connect(path.as_uri() + ('?mode=rw' if writable else '?mode=ro'),
                         uri=True, timeout=10, isolation_level=None)
    db.row_factory = sqlite3.Row
    db.execute('PRAGMA foreign_keys=ON')
    if writable:
        db.execute('PRAGMA synchronous=FULL')
    return db


def quote(name):
    require(name.replace('_', '').isalnum(), 'Unsafe schema identifier')
    return '"' + name + '"'


def facts(db):
    names = [r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")]
    return {name: sorted((canonical({k: encode(row[k]) for k in row.keys()}).decode()
                          for row in db.execute('SELECT * FROM ' + quote(name)))) for name in names}


def encode(value):
    return {'blobHex': value.hex()} if isinstance(value, bytes) else value


def healthy(db):
    require([r[0] for r in db.execute('PRAGMA integrity_check')] == ['ok'], 'SQLite integrity failure')
    require(not list(db.execute('PRAGMA foreign_key_check')), 'SQLite foreign-key failure')


def schema_facts(db):
    return [tuple(row) for row in db.execute(
        'SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name')]


def restore(source, target):
    target = location(target)
    with target.open('xb'):
        pass
    with closing(connect(source)) as original, closing(connect(target, True)) as restored:
        original.backup(restored)
        healthy(restored)
        require(facts(original) == facts(restored), 'Restored business facts differ')
        require(schema_facts(original) == schema_facts(restored), 'Restored schema differs')
        require(original.execute('PRAGMA user_version').fetchone()[0] ==
                restored.execute('PRAGMA user_version').fetchone()[0], 'Restored version differs')
