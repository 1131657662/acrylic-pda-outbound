"""Read-only integrity checks for committed V21/V28/V32/V33/V34 snapshots; no reconstruction."""
from collections import defaultdict
from pathlib import Path
import re
import time
from mysql_io import identifier
from receipts import verify_all
from safety import child, regular, require, sha, write_json


def foreign_keys(db):
    rows = db.query('SELECT TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,'
                    'REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE '
                    'WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL '
                    'ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION;')
    keys = defaultdict(list)
    for row in rows.splitlines():
        table, constraint, column, parent, target = row.split('\t')
        keys[(table, constraint, parent)].append((column, target))
    for (table, _, parent), columns in keys.items():
        join = ' AND '.join(f'c.{identifier(c)}=p.{identifier(p)}' for c, p in columns)
        present = ' AND '.join(f'c.{identifier(c)} IS NOT NULL' for c, _ in columns)
        sql = f'SELECT COUNT(*) FROM {identifier(table)} c LEFT JOIN {identifier(parent)} p ON {join} '
        sql += f'WHERE {present} AND p.{identifier(columns[0][1])} IS NULL;'
        count = db.query(sql)
        if count != '0':
            write_json(db.root / f'foreign-key-failure-{time.time_ns()}.json',
                       {'table': table, 'parent': parent, 'query': sql, 'count_result': count})
        require(count == '0', 'ORPHAN_FOREIGN_KEY')
    return len(keys)


def file_references(db, files):
    groups = [
        ("SELECT file_key,file_sha256,-1 FROM inventory_import;", '', ''),
        ("SELECT file_key,payload_sha256,byte_count FROM submission_payload;", '', ''),
        ("SELECT file_key,sha256,byte_count FROM submission_chunk;", '', ''),
    ]
    count = 0
    for query, prefix, suffix in groups:
        for row in db.query(query).splitlines():
            key, expected, size = row.split('\t')
            require(bool(re.fullmatch('[a-f0-9]{64}', expected)), 'INVALID_FILE_DIGEST')
            path = regular(child(files, prefix + key + suffix))
            require(sha(path) == expected, 'BUSINESS_FILE_HASH_MISMATCH')
            require(int(size) < 0 or path.stat().st_size == int(size), 'BUSINESS_FILE_SIZE_MISMATCH')
            count += 1
    return count


def dispatch_consistency(db):
    # Pre-aggregate each many-side independently to avoid inflated sums.
    query = '''SELECT COUNT(*) FROM order_result_version r
        LEFT JOIN (SELECT order_id,COUNT(*) n,SUM(weight_g) w FROM dispatch_record GROUP BY order_id) d
        ON d.order_id=r.order_id WHERE COALESCE(d.n,0)<>r.actual_count OR COALESCE(d.w,0)<>r.weight_g;'''
    require(db.query(query) == '0', 'RESULT_DISPATCH_MISMATCH')
    query = '''SELECT COUNT(*) FROM dispatch_record d
        LEFT JOIN inventory_ledger l ON l.dispatch_id=d.id
        LEFT JOIN order_result_version r ON r.order_id=d.order_id
        WHERE r.order_id IS NULL OR
        (r.inventory_deducted=1 AND (l.id IS NULL OR l.quantity_delta<>-1 OR l.weight_delta_g<>-d.weight_g)) OR
        (r.inventory_deducted=0 AND l.id IS NOT NULL);'''
    require(db.query(query) == '0', 'LEDGER_DEDUCTION_MISMATCH')
    query = '''SELECT COUNT(*) FROM dispatch_record d LEFT JOIN bale_commitment c
        ON c.bale_id=d.bale_id AND c.order_id=d.order_id AND c.dispatch_cycle=d.dispatch_cycle
        WHERE c.bale_id IS NULL OR c.state<>'DISPATCHED';'''
    require(db.query(query) == '0', 'DISPATCH_GUARD_MISMATCH')
    query = '''SELECT COUNT(*) FROM submission s INNER JOIN dispatch_record d ON d.submission_id=s.id
        WHERE s.status<>'SUCCEEDED';'''
    require(db.query(query) == '0', 'NON_SUCCESS_DISPATCH')


def inspect(db, paths):
    db.verify()
    version = db.query('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history;')
    require(version in ('21', '28', '32', '33', '34'), 'UNSUPPORTED_SCHEMA')
    require(db.query('SELECT COUNT(*) FROM flyway_schema_history WHERE success=0;') == '0', 'FAILED_MIGRATION')
    dispatch_consistency(db)
    result = {'foreign_keys_checked': foreign_keys(db),
              'file_references_checked': file_references(db, paths['files']),
              'signed_receipts_verified': verify_all(db, paths['config'])}
    for name, query in SUMMARY.items():
        result[name] = int(db.query(query))
    if version in ('28', '32', '33', '34'):
        from receipts import signing_keys
        from submission_archives import verify_archives as submissions
        from audit_archives import verify_archives as audit
        keys = signing_keys(paths['config'])
        result.update(submissions(db, paths['files'], keys))
        result.update(audit(db, paths['files'], keys))
    if version in ('32', '33', '34'):
        from order_counters import verify_counters
        from temporary_proofs import verify_preparations
        result.update(verify_counters(db))
        result.update(verify_preparations(db, keys))
    if version in ('33', '34'):
        from temporary_submissions import verify_submissions
        result.update(verify_submissions(db, paths['files']))
    if version == '34':
        from v34_evidence import verify_evidence
        result.update(verify_evidence(db, paths['files']))
    return result


SUMMARY = {
    'dispatch_packages': 'SELECT COUNT(*) FROM dispatch_record;',
    'dispatch_weight_g': 'SELECT COALESCE(SUM(weight_g),0) FROM dispatch_record;',
    'ledger_rows': 'SELECT COUNT(*) FROM inventory_ledger;',
    'ledger_weight_delta_g': 'SELECT COALESCE(SUM(weight_delta_g),0) FROM inventory_ledger;',
    'guard_rows': 'SELECT COUNT(*) FROM bale_commitment;',
    'success_submissions': "SELECT COUNT(*) FROM submission WHERE status='SUCCEEDED';",
    'abort_tombstones': "SELECT COUNT(*) FROM submission WHERE status='ABORTED';",
    'idempotency_rows': 'SELECT COUNT(*) FROM api_idempotency_request;',
    'audit_rows': 'SELECT COUNT(*) FROM operation_event;',
    'capture_rows': 'SELECT COUNT(*) FROM submitted_capture;',
    'device_event_rows': 'SELECT COUNT(*) FROM submitted_event;',
}
