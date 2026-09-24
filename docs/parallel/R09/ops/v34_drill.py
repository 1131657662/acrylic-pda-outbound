"""Reversible V34 faults only in an already verified, private restored copy."""
from consistency import inspect
from fault_drill import rejected
from mysql_io import Database, table_facts
from safety import child, exclusive, inventory, read_json, regular, require, write_json
from v33_drill import change, key
from v34_evidence import verify_evidence


def object_faults(db, files):
    object_id = key(db, 'SELECT HEX(id) FROM source_evidence_object ORDER BY id LIMIT 1;')
    where = f"id=UNHEX('{object_id}')"
    verify = lambda: verify_evidence(db, files)
    change(db, 'source_evidence_object', where, 'bytes_sha256', "REPEAT('0',64)",
           lambda: rejected(verify, 'EVIDENCE_FILE_HASH_MISMATCH'))
    change(db, 'source_evidence_object', where, 'byte_count', 'byte_count+1',
           lambda: rejected(verify, 'EVIDENCE_FILE_SIZE_MISMATCH'))
    change(db, 'source_evidence_object', where, 'file_key', "'r09-v34/not-present.bin'",
           lambda: rejected(verify, 'REGULAR_FILE_REQUIRED'))
    name = db.query(f'SELECT file_key FROM source_evidence_object WHERE {where};')
    path = regular(child(files, name))
    original = path.read_bytes()
    try:
        path.write_bytes(bytes([original[0] ^ 1]) + original[1:])
        rejected(verify, 'EVIDENCE_FILE_HASH_MISMATCH')
    finally:
        path.write_bytes(original)
    return ['object_digest', 'object_size', 'object_missing_file', 'object_file_corrupt']


def set_faults(db, files):
    set_id = key(db, 'SELECT HEX(id) FROM source_evidence_set ORDER BY id LIMIT 1;')
    where = f"id=UNHEX('{set_id}')"
    verify = lambda: verify_evidence(db, files)
    change(db, 'source_evidence_set', where, 'item_count', 'item_count+1',
           lambda: rejected(verify, 'EVIDENCE_ITEM_COUNT_MISMATCH'))
    change(db, 'source_evidence_set', where, 'set_sha256', "REPEAT('0',64)",
           lambda: rejected(verify, 'EVIDENCE_VERIFICATION_REFERENCE_MISMATCH'))
    return ['set_item_count', 'verification_set_hash']


def drill(settings):
    require(settings['purpose'] == 'R09_V34_SYNTHETIC_ONLY', 'V34_LAB_REQUIRED')
    with exclusive(settings['root']):
        return run_faults(settings)


def run_faults(settings):
    db = Database(settings, 'restore')
    db.verify()
    recovered = db.root / 'recovered'
    verification = read_json(recovered / 'verification.json')
    require(verification['status'] == 'PASS', 'VERIFIED_RESTORE_REQUIRED')
    require(db.query('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history;') == '34', 'UNSUPPORTED_SCHEMA')
    before = table_facts(db)
    paths = {name: recovered / name for name in ('files', 'config')}
    trees = {name: inventory(path) for name, path in paths.items()}
    baseline = inspect(db, paths)
    require(baseline['evidence_object_files_checked'] >= 2 and baseline['evidence_sets_checked'] >= 1
            and baseline['evidence_items_checked'] >= 1 and baseline['evidence_verification_rows_checked'] >= 1,
            'V34_SYNTHETIC_COVERAGE_MISSING')
    checks = object_faults(db, paths['files']) + set_faults(db, paths['files'])
    rank = db.query("SELECT installed_rank FROM flyway_schema_history WHERE version='34';")
    require(rank.isdigit(), 'FAULT_KEY_INVALID')
    change(db, 'flyway_schema_history', f'installed_rank={rank}', 'version', "'35'",
           lambda: rejected(lambda: inspect(db, paths), 'UNSUPPORTED_SCHEMA'))
    require(before == table_facts(db), 'FAULT_DRILL_CHANGED_DATABASE')
    require(trees == {name: inventory(path) for name, path in paths.items()}, 'FAULT_DRILL_CHANGED_FILES')
    require(baseline == inspect(db, paths), 'FAULT_DRILL_CHANGED_BUSINESS')
    result = {'status': 'PASS', 'checks': checks + ['unknown_schema_refused', 'all_tables_and_files_restored'],
              'scope': 'isolated retained V34 bytes and references; not trusted VERIFY or disaster reconstruction',
              'localDeletionAllowed': False}
    write_json(db.root / 'v34-fault-drill.json', result)
    return result
