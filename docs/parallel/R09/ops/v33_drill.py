"""Inject bounded, reversible faults only into a verified new V33 restored copy."""
import re
from consistency import inspect
from fault_drill import rejected
from mysql_io import Database, identifier, table_facts
from r06_drill import counter_faults
from safety import require, read_json, write_json, inventory, exclusive, child, regular
from temporary_submissions import verify_submissions


def key(db, query):
    value = db.query(query)
    require(bool(re.fullmatch('[0-9A-F]{32}', value)), 'FAULT_KEY_INVALID')
    return value


def change(db, table, predicate, field, value, check):
    table, field = identifier(table), identifier(field)
    read = f"SELECT IFNULL(HEX(CAST({field} AS BINARY)),'NULL') FROM {table} WHERE {predicate};"
    old = db.query(read)
    require(old == 'NULL' or bool(re.fullmatch('[0-9A-F]*', old)), 'FAULT_ORIGINAL_INVALID')
    db.query(f'UPDATE {table} SET {field}={value} WHERE {predicate};')
    try:
        check()
    finally:
        db.verify()
        previous = 'NULL' if old == 'NULL' else f"UNHEX('{old}')"
        db.query(f'UPDATE {table} SET {field}={previous} WHERE {predicate};')
        require(db.query(read) == old, 'FAULT_VALUE_NOT_RESTORED')


def control_faults(db, verify):
    submission = key(db, 'SELECT HEX(id) FROM submission WHERE temporary_authorization_id IS NOT NULL ORDER BY id LIMIT 1;')
    scope = key(db, f"SELECT HEX(scope_id) FROM submission WHERE id=UNHEX('{submission}');")
    auth = key(db, f"SELECT HEX(id) FROM temporary_authorization WHERE id<>(SELECT temporary_authorization_id FROM submission WHERE id=UNHEX('{submission}')) ORDER BY id LIMIT 1;")
    cases = [('submission_scope', f"id=UNHEX('{scope}')", 'work_round', '2', 'TEMP_CURRENT_ROUND_MISMATCH'),
             ('submission_scope', f"id=UNHEX('{scope}')", 'current_submission_id', 'NULL', 'TEMP_SCOPE_CURRENT_MISSING'),
             ('submission', f"id=UNHEX('{submission}')", 'temporary_authorization_id', f"UNHEX('{auth}')", 'TEMP_SOURCE_BINDING_MISMATCH'),
             ('submission', f"id=UNHEX('{submission}')", 'source_recovery_epoch', '2', 'TEMP_CONTROL_IDENTITY_MISMATCH')]
    for table, where, field, value, code in cases:
        change(db, table, where, field, value, lambda: rejected(verify, code))
    return ['scope_round', 'missing_current', 'wrong_authorization', 'wrong_original_epoch']


def body_faults(db, verify):
    submission = key(db, "SELECT HEX(s.id) FROM submission s INNER JOIN submission_payload p ON p.submission_id=s.id "
        "WHERE s.temporary_authorization_id IS NOT NULL AND s.status='REVIEW_REQUIRED' ORDER BY s.id LIMIT 1;")
    order = key(db, f"SELECT HEX(c.canonical_order_id) FROM submission_scope c INNER JOIN submission s ON s.scope_id=c.id WHERE s.id=UNHEX('{submission}');")
    capture = key(db, f"SELECT HEX(id) FROM submitted_capture WHERE submission_id=UNHEX('{submission}') ORDER BY id LIMIT 1;")
    event = key(db, f"SELECT HEX(id) FROM submitted_event WHERE submission_id=UNHEX('{submission}') ORDER BY id LIMIT 1;")
    cases = [('order_submission_slot', f"order_id=UNHEX('{order}')", 'work_round', '2', 'TEMP_TARGET_SLOT_MISMATCH'),
             ('outbound_order', f"id=UNHEX('{order}')", 'source', "'WEB'", 'TEMP_TARGET_SLOT_MISMATCH'),
             ('order_revision', f"order_id=UNHEX('{order}')", 'vehicle_no', "'R09 wrong plan'", 'TEMP_TARGET_PLAN_MISMATCH'),
             ('submitted_capture', f"id=UNHEX('{capture}')", 'record_sha256', "REPEAT('0',64)", 'TEMP_CAPTURE_INDEX_MISMATCH'),
             ('submitted_event', f"id=UNHEX('{event}')", 'record_sha256', "REPEAT('0',64)", 'TEMP_EVENT_INDEX_MISMATCH')]
    for table, where, field, value, code in cases:
        change(db, table, where, field, value, lambda: rejected(verify, code))
    return ['slot_round', 'wrong_target_type', 'changed_plan', 'capture_hash', 'event_hash']


def upload_faults(db, verify):
    upload = key(db, 'SELECT HEX(u.submission_id) FROM submission_upload u INNER JOIN submission s ON s.id=u.submission_id '
        'WHERE s.temporary_authorization_id IS NOT NULL ORDER BY u.submission_id LIMIT 1;')
    where = f"submission_id=UNHEX('{upload}')"
    change(db, 'submission_upload', where, 'chunk_count', 'chunk_count+1',
           lambda: rejected(verify, 'TEMP_UPLOAD_GEOMETRY_MISMATCH'))
    chunk = db.query('SELECT HEX(c.submission_id),c.chunk_index FROM submission_chunk c INNER JOIN submission s ON s.id=c.submission_id '
        'WHERE s.temporary_authorization_id IS NOT NULL ORDER BY c.submission_id,c.chunk_index LIMIT 1;').split('\t')
    require(len(chunk) == 2 and re.fullmatch('[0-9A-F]{32}', chunk[0]) and chunk[1].isdigit(), 'FAULT_KEY_INVALID')
    where = f"submission_id=UNHEX('{chunk[0]}') AND chunk_index={chunk[1]}"
    change(db, 'submission_chunk', where, 'sha256', "REPEAT('0',64)", lambda: rejected(verify, 'TEMP_CHUNK_FILE_MISMATCH'))
    aborted = key(db, "SELECT HEX(s.id) FROM submission s INNER JOIN submission_upload u ON u.submission_id=s.id "
        "LEFT JOIN submission_payload p ON p.submission_id=s.id WHERE s.temporary_authorization_id IS NOT NULL "
        "AND s.status='ABORTED' AND p.submission_id IS NULL ORDER BY s.id LIMIT 1;")
    change(db, 'submission', f"id=UNHEX('{aborted}')", 'receipt_text', "JSON_SET(receipt_text,'$.payloadSha256',REPEAT('0',64))",
           lambda: rejected(verify, 'TEMP_ABORT_CONTROL_MISMATCH'))
    change(db, 'submission', f"id=UNHEX('{aborted}')", 'receipt_text', "JSON_SET(receipt_text,'$.stockReleased',CAST('true' AS JSON))",
           lambda: rejected(verify, 'TEMP_ABORT_AUTHORITY_MISMATCH'))
    return ['upload_geometry', 'chunk_file_hash', 'declared_hash_not_accepted', 'abort_cannot_release']


def file_fault(db, files, verify):
    name = db.query('SELECT p.file_key FROM submission_payload p INNER JOIN submission s ON s.id=p.submission_id '
        'WHERE s.temporary_authorization_id IS NOT NULL ORDER BY s.id LIMIT 1;')
    path = regular(child(files, name))
    original = path.read_bytes()
    try:
        path.write_bytes(bytes([original[0] ^ 1]) + original[1:])
        rejected(verify, 'TEMP_PAYLOAD_FILE_MISMATCH')
    finally:
        path.write_bytes(original)


def drill(settings):
    with exclusive(settings['root']):
        return run_faults(settings)


def run_faults(settings):
    db = Database(settings, 'restore')
    db.verify()
    recovered = db.root / 'recovered'
    require(read_json(recovered / 'verification.json')['status'] == 'PASS', 'VERIFIED_RESTORE_REQUIRED')
    require(db.query('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history;') == '33', 'UNSUPPORTED_SCHEMA')
    before = table_facts(db)
    paths = {name: recovered / name for name in ('files', 'config')}
    trees = {name: inventory(path) for name, path in paths.items()}
    inspect(db, paths)
    verify = lambda: verify_submissions(db, paths['files'])
    checks = control_faults(db, verify) + body_faults(db, verify) + upload_faults(db, verify)
    file_fault(db, paths['files'], verify)
    counter_faults(db)
    rank = db.query("SELECT installed_rank FROM flyway_schema_history WHERE version='33';")
    require(rank.isdigit(), 'FAULT_KEY_INVALID')
    change(db, 'flyway_schema_history', f'installed_rank={rank}', 'version', "'34'", lambda: rejected(lambda: inspect(db, paths), 'UNSUPPORTED_SCHEMA'))
    require(before == table_facts(db), 'FAULT_DRILL_CHANGED_DATABASE')
    require(trees == {name: inventory(path) for name, path in paths.items()}, 'FAULT_DRILL_CHANGED_FILES')
    inspect(db, paths)
    result = {'status': 'PASS', 'checks': checks + ['payload_file_hash', 'counter_regressed', 'counter_missing',
        'unknown_schema_refused', 'all_tables_and_files_restored'], 'localDeletionAllowed': False}
    write_json(db.root / 'v33-fault-drill.json', result)
    return result
