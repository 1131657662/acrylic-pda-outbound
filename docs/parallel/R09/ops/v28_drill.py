"""Inject reversible faults only into the verified V28 R09 restore, then compare all facts."""
from pathlib import Path
import re
from archive_proofs import parse
from audit_archives import verify_archives as audit
from submission_archives import verify_archives as submissions
from receipts import signing_keys
from mysql_io import Database, identifier, table_facts
from jobs_monitor import collect
from fault_drill import rejected
from safety import child, regular, require, read_json, write_json, inventory


def change(db, table, predicate, assignments, check):
    names = list(assignments)
    old = db.query('SELECT ' + ','.join(f'HEX(CAST({identifier(n)} AS CHAR))' for n in names) +
                   f' FROM {identifier(table)} WHERE {predicate};').split('\t')
    require(len(old) == len(names) and all(re.fullmatch('[0-9A-F]*', value) for value in old), 'FAULT_ORIGINAL_INVALID')
    update = f'UPDATE {identifier(table)} SET '
    suffix = f' WHERE {predicate};'
    db.query(update + ','.join(f'{identifier(n)}={assignments[n]}' for n in names) + suffix)
    try:
        check()
    finally:
        db.verify()
        values = [f"{identifier(n)}=CONVERT(UNHEX('{value}') USING utf8mb4)" for n, value in zip(names, old)]
        db.query(update + ','.join(values) + suffix)


def archive_faults(db, files, keys):
    sid, epoch = db.query('SELECT HEX(submission_id),recovery_epoch FROM submission_archive ORDER BY submission_id LIMIT 1;').split('\t')
    require(bool(re.fullmatch('[0-9A-F]{32}', sid)) and epoch.isdigit(), 'FAULT_KEY_INVALID')
    where = f"submission_id=UNHEX('{sid}') AND recovery_epoch={epoch}"
    change(db, 'submission_archive', where, {'receipt_sha256': "REPEAT('0',64)"},
           lambda: rejected(lambda: submissions(db, files, keys), 'ARCHIVE_INDEX_MISMATCH'))
    device, epoch, after = db.query('SELECT HEX(origin_device_id),recovery_epoch,after_seq FROM pda_audit_archive '
                                  'ORDER BY origin_device_id,after_seq LIMIT 1;').split('\t')
    require(bool(re.fullmatch('[0-9A-F]{32}', device)) and epoch.isdigit() and after.isdigit(), 'FAULT_KEY_INVALID')
    where = f"origin_device_id=UNHEX('{device}') AND recovery_epoch={epoch} AND after_seq={after}"
    change(db, 'pda_audit_archive', where, {'through_seq': 'through_seq-1'},
           lambda: rejected(lambda: audit(db, files, keys), 'AUDIT_ARCHIVE_INDEX_MISMATCH'))
    change(db, 'pda_audit_event', f"origin_device_id=UNHEX('{device}') AND local_seq=1", {'record_sha256': "REPEAT('0',64)"},
           lambda: rejected(lambda: audit(db, files, keys), 'AUDIT_EVENT_HASH_MISMATCH'))
    change(db, 'pda_audit_checkpoint', f"origin_device_id=UNHEX('{device}') AND recovery_epoch={epoch}", {'through_seq': 'through_seq+1'},
           lambda: rejected(lambda: audit(db, files, keys), 'AUDIT_CHECKPOINT_GAP'))
    change(db, 'pda_audit_checkpoint', f"origin_device_id=UNHEX('{device}') AND recovery_epoch={epoch}", {'through_seq': '0'},
           lambda: rejected(lambda: audit(db, files, keys), 'AUDIT_ARCHIVE_CHECKPOINT_MISSING'))
    missing_archive(db, files, keys)


def missing_archive(db, files, keys):
    checksum = db.query('SELECT payload_sha256 FROM pda_audit_archive ORDER BY origin_device_id,after_seq LIMIT 1;')
    require(bool(re.fullmatch('[a-f0-9]{64}', checksum)), 'FAULT_KEY_INVALID')
    path = regular(child(files, f'submissions/{checksum}.json'))
    held = path.with_suffix('.r09-held')
    require(not held.exists(), 'FAULT_HOLD_PATH_EXISTS')
    path.rename(held)
    try:
        rejected(lambda: audit(db, files, keys), 'REGULAR_FILE_REQUIRED')
    finally:
        require(not path.exists(), 'FAULT_FILE_PATH_REUSED')
        held.rename(path)


def task_faults(db):
    key = db.query("SELECT HEX(id) FROM background_job WHERE status='SUCCEEDED' ORDER BY id LIMIT 1;")
    require(bool(re.fullmatch('[0-9A-F]{32}', key)), 'FAULT_KEY_INVALID')
    where = f"id=UNHEX('{key}')"
    change(db, 'background_job', where, {'status': "'FAILED'"}, lambda: task_detected(db, 'failed'))
    change(db, 'background_job', where, {'status': "'RETRY'", 'available_at': "'2000-01-01 00:00:00'"},
           lambda: task_detected(db, 'oldest_ready_seconds'))


def task_detected(db, metric):
    result = collect(db)
    require(result['status'] == 'FAIL' and metric in result['exceeded'], 'TASK_FAULT_NOT_DETECTED')


def drill(settings):
    db = Database(settings, 'restore')
    db.verify()
    recovered = db.root / 'recovered'
    require(read_json(recovered / 'verification.json')['status'] == 'PASS', 'VERIFIED_RESTORE_REQUIRED')
    before = table_facts(db)
    trees = {name: inventory(recovered / name) for name in ('files', 'config')}
    keys = signing_keys(recovered / 'config')
    archive_faults(db, recovered / 'files', keys)
    task_faults(db)
    after = table_facts(db)
    if after != before:
        write_json(db.root / 'v28-fault-differences.json', {t: {'before': before[t], 'after': after.get(t)}
                                                       for t in before if before[t] != after.get(t)})
    require(after == before, 'FAULT_DRILL_CHANGED_DATABASE')
    require(trees == {name: inventory(recovered / name) for name in trees}, 'FAULT_DRILL_CHANGED_FILES')
    result = {'status': 'PASS', 'checks': ['submission_archive_index', 'audit_archive_index', 'original_audit_hash',
        'audit_checkpoint_gap', 'archive_checkpoint_regressed', 'missing_audit_archive', 'failed_task', 'overdue_task', 'all_tables_and_files_restored']}
    write_json(db.root / 'v28-fault-drill.json', result)
    return result
