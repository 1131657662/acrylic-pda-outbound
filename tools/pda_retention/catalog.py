"""Reviewed formal deletion boundary; unknown schema fails closed."""
from contextlib import closing
import sqlite3

from .codec import digest, parse, require
from .product_triggers import validate as validate_triggers
from .store import ROOT, quote

# A schema change must prompt a new full reference review, even if Room accepts it.
REVIEWED_IDENTITY = '7aa6baf1b8965d6eea721ac5ce439a9c'
OWNED = ('local_submission_archive', 'local_submission', 'work_event', 'work_scan',
         'work_capture', 'work_line', 'work_order', 'prepared_bale', 'prepared_order')
GLOBAL = {'work_device', 'local_sync_state', 'local_cloud_order', 'local_cloud_barcode',
          'local_recovery_control', 'local_retained_baseline', 'room_master_table', 'android_metadata'}


def schema():
    directory = ROOT / 'android/app/schemas/com.acrylic.outbound.pda.data.PdaDatabase'
    candidates = [p for p in directory.glob('*.json') if p.stem.isdigit()]
    raw = max(candidates, key=lambda p: int(p.stem)).read_bytes()
    model = parse(raw)['database']
    require(model['identityHash'] == REVIEWED_IDENTITY, 'Schema changed; reference review required')
    return model, digest(raw)


def validate(db, trigger_profile='SYNTHETIC_SCHEMA_ONLY'):
    model, sha = schema()
    expected = {e['tableName']: e for e in model['entities']}
    actual = {r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    require(set(expected) <= actual <= set(expected) | {'room_master_table', 'android_metadata'},
            'Missing or unknown tables')
    require(db.execute('PRAGMA user_version').fetchone()[0] == model['version'], 'Schema version mismatch')
    validate_triggers(db, trigger_profile)
    with closing(sqlite3.connect(':memory:')) as reference:
        create(reference, model)
        for table in [*expected, 'room_master_table']:
            require(structure(db, table) == structure(reference, table), 'Schema changed: ' + table)
    require(list(db.execute('SELECT identity_hash FROM room_master_table WHERE id=42'))[0][0]
            == REVIEWED_IDENTITY, 'Room identity mismatch')
    return expected, sha


def create(db, model):
    for entity in model['entities']:
        db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        for index in entity.get('indices', []):
            db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    for query in model['setupQueries']:
        db.execute(query)
    db.execute('PRAGMA user_version=' + str(model['version']))


def structure(db, table):
    columns = [tuple(r) for r in db.execute('PRAGMA table_xinfo(' + quote(table) + ')')]
    foreign = sorted(tuple(r)[2:] for r in db.execute('PRAGMA foreign_key_list(' + quote(table) + ')'))
    indices = []
    for index in db.execute('PRAGMA index_list(' + quote(table) + ')'):
        values = [tuple(r)[1:] for r in db.execute('PRAGMA index_xinfo(' + quote(index[1]) + ')')]
        indices.append((index[2], index[3], index[4], values))
    definitions = [tuple(r) for r in db.execute(
        "SELECT type,name,sql FROM sqlite_master WHERE tbl_name=? AND type IN ('table','index') ORDER BY type,name", (table,))]
    return columns, foreign, sorted(indices), definitions
