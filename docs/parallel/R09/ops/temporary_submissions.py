"""Fail closed on V33 TEMP source/control and pending-only state inconsistencies."""
from uuid import UUID
from archive_proofs import parse
from safety import require
from temporary_uploads import verify_upload

ROWS = '''SELECT JSON_OBJECT('id',BIN_TO_UUID(s.id),'scope',BIN_TO_UUID(c.id),
 'warehouse',BIN_TO_UUID(c.warehouse_id),'formal',BIN_TO_UUID(c.source_order_id),
 'creator',BIN_TO_UUID(c.creator_device_id),'client',BIN_TO_UUID(c.client_order_id),
 'order',BIN_TO_UUID(c.canonical_order_id),'scopeRound',c.work_round,'current',BIN_TO_UUID(c.current_submission_id),
 'assignment',BIN_TO_UUID(s.assignment_id),'continuation',BIN_TO_UUID(s.continuation_id),
 'auth',BIN_TO_UUID(s.temporary_authorization_id),'freeze',BIN_TO_UUID(s.freeze_device_id),
 'actor',BIN_TO_UUID(s.operator_id),'round',s.work_round,'epoch',s.source_recovery_epoch,
 'status',s.status,'receipt',s.receipt_text,'issues',s.issues_json,'originAuth',BIN_TO_UUID(o.authorization_id),
 'authDevice',BIN_TO_UUID(a.device_id),'authUser',BIN_TO_UUID(a.user_id),'authEpoch',a.recovery_epoch,
 'authWarehouse',BIN_TO_UUID(a.warehouse_id),'manifest',a.manifest_text)
 FROM submission s INNER JOIN submission_scope c ON c.id=s.scope_id
 LEFT JOIN temporary_work_origin o ON o.creator_device_id=c.creator_device_id AND o.client_order_id=c.client_order_id
 LEFT JOIN temporary_authorization a ON a.id=s.temporary_authorization_id
 WHERE c.creator_device_id IS NOT NULL OR s.temporary_authorization_id IS NOT NULL ORDER BY s.id;'''


def source(row):
    return {'kind': 'TEMP', 'creatorDeviceId': row['creator'], 'clientOrderId': row['client']}


def control(row):
    require(row['formal'] is None and row['assignment'] is None and row['continuation'] is None,
            'TEMP_CONTROL_SHAPE_MISMATCH')
    require(row['creator'] and row['client'] and row['auth'] == row['originAuth'] and row['auth'] is not None,
            'TEMP_SOURCE_BINDING_MISMATCH')
    require(row['creator'] == row['freeze'] == row['authDevice'] and row['actor'] == row['authUser']
            and row['epoch'] == row['authEpoch'] and row['warehouse'] == row['authWarehouse'], 'TEMP_CONTROL_IDENTITY_MISMATCH')
    require(row['current'] == row['id'] and row['scopeRound'] == row['round'] == 1, 'TEMP_CURRENT_ROUND_MISMATCH')
    require(row['status'] in ('REGISTERED', 'RECEIVING', 'REVIEW_REQUIRED', 'ABORTED'), 'TEMP_STATE_UNSUPPORTED')


def abort_receipt(row, payload):
    if row['status'] != 'ABORTED':
        require(row['receipt'] is None, 'TEMP_UNEXPECTED_TERMINAL_RECEIPT')
        return
    receipt = parse(row['receipt'])
    expected = {'submissionId': row['id'], 'orderId': row['order'], 'status': 'ABORTED',
                'source': source(row), 'freezeDeviceId': row['freeze'], 'workRound': str(row['round']),
                'issuedRecoveryEpoch': str(row['epoch']), 'payloadSha256': payload['hash'] if payload else None}
    require(all(receipt.get(k) == value for k, value in expected.items()), 'TEMP_ABORT_CONTROL_MISMATCH')
    require(receipt.get('payloadPresent') is bool(payload) and receipt.get('stockReleased') is False
            and receipt.get('mayResumeScanning') is False, 'TEMP_ABORT_AUTHORITY_MISMATCH')


def target(db, row, payload):
    key = UUID(row['id']).hex
    if not payload:
        require(row['order'] is None and row['status'] != 'REVIEW_REQUIRED', 'TEMP_BODYLESS_FORMALIZATION')
        for table in ('submitted_capture', 'submitted_event', 'bale_commitment'):
            require(db.query(f"SELECT COUNT(*) FROM {table} WHERE submission_id=UNHEX('{key}');") == '0',
                    'TEMP_BODYLESS_EVIDENCE')
        require(db.query(f"SELECT COUNT(*) FROM order_submission_slot WHERE scope_id=UNHEX('{UUID(row['scope']).hex}');") == '0',
                'TEMP_BODYLESS_SLOT')
        return
    require(row['order'] is not None and row['status'] in ('REVIEW_REQUIRED', 'ABORTED'), 'TEMP_PAYLOAD_STATE_MISMATCH')
    order = UUID(row['order']).hex
    actual = db.query('SELECT o.source,o.status,BIN_TO_UUID(o.warehouse_id),BIN_TO_UUID(o.created_by),'
        'BIN_TO_UUID(t.scope_id),t.work_round,BIN_TO_UUID(t.current_submission_id),t.phase '
        'FROM outbound_order o LEFT JOIN order_submission_slot t ON t.order_id=o.id '
        f"WHERE o.id=UNHEX('{order}');").split('\t')
    require(actual == ['PDA_TEMP', 'REVIEW_REQUIRED', row['warehouse'], row['actor'], row['scope'],
                       str(row['round']), row['id'], 'FROZEN'], 'TEMP_TARGET_SLOT_MISMATCH')
    for table in ('order_assignment', 'order_result_version', 'dispatch_record'):
        require(db.query(f"SELECT COUNT(*) FROM {table} WHERE order_id=UNHEX('{order}');") == '0', 'TEMP_PREMATURE_RELEASE')


def verify_submissions(db, files):
    from temporary_payloads import payload_record, verify_payload
    require(db.query('SELECT COUNT(*) FROM submission_scope c LEFT JOIN submission s ON s.id=c.current_submission_id '
        'WHERE c.creator_device_id IS NOT NULL AND (s.id IS NULL OR s.scope_id<>c.id);') == '0', 'TEMP_SCOPE_CURRENT_MISSING')
    require(db.query('SELECT COUNT(*) FROM (SELECT canonical_order_id FROM submission_scope '
        'WHERE canonical_order_id IS NOT NULL GROUP BY canonical_order_id HAVING COUNT(*)<>1) duplicates;') == '0',
        'TEMP_DUPLICATE_TARGET')
    require(db.query("SELECT COUNT(*) FROM outbound_order o LEFT JOIN submission_scope c ON c.canonical_order_id=o.id "
        "WHERE o.source='PDA_TEMP' AND c.id IS NULL;") == '0', 'TEMP_TARGET_SOURCE_MISSING')
    rows = [parse(line) for line in db.query(ROWS).splitlines()]
    bodies, chunks, aborts = 0, 0, 0
    for row in rows:
        control(row)
        key = UUID(row['id']).hex
        payload = payload_record(db, key)
        target(db, row, payload)
        chunks += verify_upload(db, files, key, payload, row['status'])
        abort_receipt(row, payload)
        if payload:
            verify_payload(db, files, row, payload)
            bodies += 1
        aborts += row['status'] == 'ABORTED'
    return {'temporary_submissions_verified': len(rows), 'temporary_payloads_verified': bodies,
            'temporary_chunks_verified': chunks, 'temporary_aborts_verified': aborts}
