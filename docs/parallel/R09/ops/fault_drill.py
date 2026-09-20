"""Exercise faults only inside a successfully restored, dedicated R09 lab."""
import json
from pathlib import Path
import shutil
import time
from backup import restore
from consistency import file_references, dispatch_consistency
from lab import load
from monitor import freshness, disk
from mysql_io import Database, table_facts
from receipts import signing_keys, verify_receipt
from safety import child, require, regular, sha, read_json, write_json, OpsError


def rejected(action, expected):
    try:
        action()
    except Exception as error:
        matches = isinstance(error, expected) if isinstance(expected, type) else isinstance(error, OpsError) and str(error) == expected
        require(matches, 'UNEXPECTED_FAULT_FAILURE')
        return True
    raise OpsError('FAULT_WAS_NOT_DETECTED')


def file_faults(db, recovered):
    key = db.query('SELECT file_key FROM submission_payload ORDER BY submission_id LIMIT 1;')
    path = regular(child(recovered / 'files', key))
    held = path.with_suffix('.r09-held')
    require(not held.exists(), 'FAULT_HOLD_PATH_EXISTS')
    original = path.read_bytes()
    expected = sha(path)
    path.rename(held)
    try:
        rejected(lambda: file_references(db, recovered / 'files'), 'REGULAR_FILE_REQUIRED')
    finally:
        require(not path.exists(), 'FAULT_FILE_PATH_REUSED')
        held.rename(path)
    path.write_bytes(bytes([original[0] ^ 1]) + original[1:])
    try:
        rejected(lambda: file_references(db, recovered / 'files'), 'BUSINESS_FILE_HASH_MISMATCH')
    finally:
        regular(child(recovered / 'files', key)).write_bytes(original)
    require(sha(path) == expected, 'FAULT_FILE_NOT_RESTORED')


def signature_faults(db, recovered):
    encoded = db.query("SELECT HEX(receipt_text) FROM submission WHERE status='SUCCEEDED' ORDER BY id LIMIT 1;")
    receipt = json.loads(bytes.fromhex(encoded))
    receipt['actualCount'] = str(int(receipt['actualCount']) + 1)
    rejected(lambda: verify_receipt(receipt, signing_keys(recovered / 'config')), 'RECEIPT_BODY_MISMATCH')
    private = regular(child(recovered, 'config/private.pk8'))
    original = private.read_bytes()
    private.write_bytes(b'invalid synthetic key')
    try:
        rejected(lambda: signing_keys(recovered / 'config'), ValueError)
    finally:
        regular(child(recovered, 'config/private.pk8')).write_bytes(original)
    signing_keys(recovered / 'config')


def ledger_fault(db):
    key, weight = db.query('SELECT HEX(id),weight_delta_g FROM inventory_ledger ORDER BY id LIMIT 1;').split('\t')
    require(len(key) == 32 and all(c in '0123456789ABCDEF' for c in key), 'FAULT_KEY_INVALID')
    original = int(weight)
    db.query(f"UPDATE inventory_ledger SET weight_delta_g={original - 1} WHERE id=UNHEX('{key}');")
    try:
        rejected(lambda: dispatch_consistency(db), 'LEDGER_DEDUCTION_MISMATCH')
    finally:
        db.verify()
        db.query(f"UPDATE inventory_ledger SET weight_delta_g={original} WHERE id=UNHEX('{key}');")
    dispatch_consistency(db)


def drill(settings, backup):
    db = Database(settings, 'restore')
    db.verify()
    recovered = db.root / 'recovered'
    require(read_json(recovered / 'verification.json')['status'] == 'PASS', 'VERIFIED_RESTORE_REQUIRED')
    before = table_facts(db)
    rejected(lambda: restore(settings, backup), 'RESTORE_DATABASE_NOT_EMPTY')
    rejected(lambda: db.query('SELECT COUNT(*) FROM mysql.user;'), 'MYSQL_QUERY_FAILED')
    rejected(lambda: Database(settings, 'source').import_file(backup / 'schema.sql'), 'RESTORE_TARGET_REQUIRED')
    file_faults(db, recovered)
    signature_faults(db, recovered)
    ledger_fault(db)
    config = {'directory': str(backup.parent), 'max_age_seconds': 93600}
    freshness(config, time.time())
    rejected(lambda: freshness(config, time.time() + 100000), 'BACKUP_STALE')
    rejected(lambda: disk({'path': str(recovered), 'minimum_free_bytes': 10**30, 'minimum_free_percent': 0}), 'DISK_FREE_BYTES_LOW')
    after = table_facts(db)
    if after != before:
        write_json(db.root / ('fault-differences-' + str(time.time_ns()) + '.json'),
                   {t: {'before': before[t], 'after': after.get(t)} for t in before if before[t] != after.get(t)})
    require(after == before, 'FAULT_DRILL_CHANGED_DATABASE')
    from safety import inventory
    expected = read_json(backup / 'snapshot.json')['trees']
    require({name: inventory(recovered / name) for name in expected} == expected, 'FAULT_DRILL_CHANGED_FILES')
    result = {'status': 'PASS', 'checks': ['nonempty_restore_refused', 'system_database_access_refused',
        'source_import_refused', 'missing_file_detected', 'changed_file_detected', 'forged_receipt_detected',
        'invalid_private_key_detected', 'ledger_weight_difference_detected', 'stale_backup_detected',
        'low_disk_detected', 'database_and_files_unchanged_after_faults']}
    write_json(db.root / 'fault-drill.json', result)
    return result


if __name__ == '__main__':
    import sys
    try:
        print(json.dumps(drill(load(sys.argv[1]), Path(sys.argv[2]).resolve())))
    except Exception as error:
        print(json.dumps({'status': 'FAIL', 'code': str(error) if isinstance(error, OpsError) else 'FAULT_DRILL_FAILED'}))
        sys.exit(2)
