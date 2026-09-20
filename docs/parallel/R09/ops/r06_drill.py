"""Reversible V32 faults run only after a verified dedicated R09 restore."""
import re
from audit_archives import verify_archives
from temporary_proofs import verify_preparations
from order_counters import verify_counters
from receipts import signing_keys
from v28_drill import change
from fault_drill import rejected
from mysql_io import Database, table_facts
from safety import require, read_json, write_json, inventory, exclusive


def counter_faults(db):
    day, previous = db.query('SELECT business_date,last_sequence FROM order_number_counter '
                              'WHERE last_sequence>0 ORDER BY business_date LIMIT 1;').split('\t')
    require(bool(re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2}', day)) and previous.isdigit(), 'FAULT_KEY_INVALID')
    where = f"business_date='{day}'"
    change(db, 'order_number_counter', where, {'last_sequence': '0'},
           lambda: rejected(lambda: verify_counters(db), 'ORDER_COUNTER_BEHIND'))
    db.query(f'DELETE FROM order_number_counter WHERE {where};')
    try:
        rejected(lambda: verify_counters(db), 'ORDER_COUNTER_MISSING')
    finally:
        db.verify()
        db.query(f"INSERT INTO order_number_counter(business_date,last_sequence) VALUES('{day}',{previous});")


def temporary_faults(db, files, keys):
    event, auth = db.query('SELECT HEX(id),HEX(temporary_authorization_id) FROM pda_audit_event '
                           'WHERE assignment_id IS NULL ORDER BY id LIMIT 1;').split('\t')
    require(all(re.fullmatch('[0-9A-F]{32}', key) for key in (event, auth)), 'FAULT_KEY_INVALID')
    change(db, 'pda_audit_event', f"id=UNHEX('{event}')", {'source_recovery_epoch': 'source_recovery_epoch+1'},
           lambda: rejected(lambda: verify_archives(db, files, keys), 'AUDIT_TEMP_PROVENANCE_MISMATCH'))
    change(db, 'temporary_authorization', f"id=UNHEX('{auth}')", {'manifest_sha256': "REPEAT('0',64)"},
           lambda: rejected(lambda: verify_preparations(db, keys), 'TEMP_MANIFEST_HASH_MISMATCH'))
    change(db, 'temporary_authorization', f"id=UNHEX('{auth}')",
           {'signed_token': "CONCAT(SUBSTRING_INDEX(signed_token,'.',2),'.',REPEAT('A',512))"},
           lambda: rejected(lambda: verify_preparations(db, keys), 'TEMP_SIGNATURE_INVALID'))
    missing_part(db, auth, keys)


def missing_part(db, auth, keys):
    where = f"authorization_id=UNHEX('{auth}') AND part_name='rules'"
    original = db.query(f'SELECT HEX(compressed) FROM temporary_preparation_part WHERE {where};')
    require(bool(re.fullmatch('[0-9A-F]+', original)), 'FAULT_ORIGINAL_INVALID')
    db.query(f'DELETE FROM temporary_preparation_part WHERE {where};')
    try:
        rejected(lambda: verify_preparations(db, keys), 'TEMP_PART_SET_MISMATCH')
    finally:
        db.verify()
        db.query('INSERT INTO temporary_preparation_part(authorization_id,part_name,compressed) '
                 f"VALUES(UNHEX('{auth}'),'rules',UNHEX('{original}'));")


def drill(settings):
    with exclusive(settings['root']):
        return run_faults(settings)


def run_faults(settings):
    db = Database(settings, 'restore')
    db.verify()
    recovered = db.root / 'recovered'
    require(read_json(recovered / 'verification.json')['status'] == 'PASS', 'VERIFIED_RESTORE_REQUIRED')
    require(db.query('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history;') == '32', 'UNSUPPORTED_SCHEMA')
    before = table_facts(db)
    trees = {name: inventory(recovered / name) for name in ('files', 'config')}
    keys = signing_keys(recovered / 'config')
    counter_faults(db)
    temporary_faults(db, recovered / 'files', keys)
    require(before == table_facts(db), 'FAULT_DRILL_CHANGED_DATABASE')
    require(trees == {name: inventory(recovered / name) for name in trees}, 'FAULT_DRILL_CHANGED_FILES')
    result = {'status': 'PASS', 'checks': ['counter_regressed', 'counter_missing', 'temp_original_epoch_mismatch',
        'temp_manifest_changed', 'temp_signature_changed', 'temp_part_missing', 'all_tables_and_files_restored']}
    write_json(db.root / 'r06-fault-drill.json', result)
    return result
