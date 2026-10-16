"""Exact, source-pinned native trigger boundary; never execute SQL from an input database."""
from pathlib import Path

from .codec import digest, parse, require
from .store import ROOT

REVIEWED_MANIFEST = '8ef30d0a17ce896e0309828e573067777695512c8d4693ca4b0fc219bb0d0db5'


def reviewed():
    raw = Path(__file__).with_name('product-triggers.json').read_bytes().replace(b'\r\n', b'\n')
    require(digest(raw) == REVIEWED_MANIFEST, 'Product trigger manifest changed; review required')
    model = parse(raw)
    for source in model['sourceFiles']:
        require(digest((ROOT / source['path']).read_bytes().replace(b'\r\n', b'\n')) == source['sha256'],
                'Product trigger source changed; review required')
    require(all(digest(row['sql'].encode()) == row['sha256'] for row in model['triggers']),
            'Product trigger definition hash mismatch')
    return model['triggers']


def validate(db, profile):
    require(profile in ('SYNTHETIC_SCHEMA_ONLY', 'PRODUCT_NATIVE'), 'Unknown trigger profile')
    require(not list(db.execute("SELECT name FROM sqlite_master WHERE type='view'")),
            'Views require reference review')
    actual = [tuple(row) for row in db.execute(
        "SELECT name,tbl_name,sql FROM sqlite_master WHERE type='trigger' ORDER BY name")]
    if profile == 'SYNTHETIC_SCHEMA_ONLY':
        require(not actual, 'Synthetic schema must have no triggers')
        return
    expected = [(row['name'], row['table'], row['sql']) for row in reviewed()]
    require(actual == expected, 'Product trigger definitions changed or incomplete')
