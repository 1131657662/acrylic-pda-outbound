"""Validate V27 archive index, signature, original receipt and frozen submission."""
from archive_proofs import parse, content, verify_proof, instant
from receipts import verify_receipt
from safety import require

QUERY = '''SELECT BIN_TO_UUID(a.submission_id),a.recovery_epoch,a.payload_sha256,a.receipt_sha256,
    a.archived_at,a.retain_until,HEX(a.confirmation_text),HEX(s.receipt_text),s.status,
    BIN_TO_UUID(c.source_order_id),BIN_TO_UUID(s.assignment_id),BIN_TO_UUID(c.warehouse_id),
    BIN_TO_UUID(s.operator_id),BIN_TO_UUID(s.freeze_device_id),s.work_round,s.source_recovery_epoch,
    p.payload_sha256,p.byte_count
    FROM submission_archive a INNER JOIN submission s ON s.id=a.submission_id
    INNER JOIN submission_scope c ON c.id=s.scope_id
    INNER JOIN submission_payload p ON p.submission_id=s.id ORDER BY a.submission_id,a.recovery_epoch;'''


def verify_archives(db, files, keys):
    rows = db.query(QUERY).splitlines()
    require(len(rows) == int(db.query('SELECT COUNT(*) FROM submission_archive;')), 'ARCHIVE_SOURCE_MISSING')
    for line in rows:
        row = line.split('\t')
        proof = parse(bytes.fromhex(row[6]))
        verify_proof(proof, keys, 'acrylic-pda-archive')
        verify_index(row, proof)
        body = content(files, row[2], proof['payloadBytes'])
        receipt = content(files, row[3], proof['receiptBytes'])
        require(receipt == parse(bytes.fromhex(row[7])), 'ARCHIVE_ORIGINAL_RECEIPT_MISMATCH')
        verify_receipt(receipt, keys)
        require(receipt['submissionId'] == row[0] and receipt['status'] == row[8] == 'SUCCEEDED', 'ARCHIVE_TERMINAL_MISMATCH')
        require(receipt['payloadSha256'] == row[2] == row[16], 'ARCHIVE_PAYLOAD_MISMATCH')
        require(int(proof['payloadBytes']) == int(row[17]), 'ARCHIVE_PAYLOAD_SIZE_MISMATCH')
        require(proof['completedAt'] == receipt['completedAt'], 'ARCHIVE_COMPLETION_MISMATCH')
        require(instant(proof['archivedAt']) >= instant(proof['completedAt']), 'ARCHIVE_COMPLETION_MISMATCH')
        require(body['source']['sourceOrderId'] == proof['orderId'], 'ARCHIVE_FROZEN_SOURCE_MISMATCH')
        for name in ('submissionId', 'assignmentId', 'freezeDeviceId', 'workRound', 'sourceRecoveryEpoch'):
            require(body[name] == proof[name], 'ARCHIVE_FROZEN_SOURCE_MISMATCH')
    return {'submission_archives_verified': len(rows), 'submission_archive_file_references': 2 * len(rows)}


def verify_index(row, proof):
    names = ('submissionId', 'recoveryEpoch', 'payloadSha256', 'receiptSha256')
    for name, value in zip(names, row[:4]):
        require(str(proof[name]) == value, 'ARCHIVE_INDEX_MISMATCH')
    for name, value in zip(('archivedAt', 'retainUntil'), row[4:6]):
        require(instant(proof[name]) == instant(value + '+00:00'), 'ARCHIVE_INDEX_MISMATCH')
    names = ('orderId', 'assignmentId', 'warehouseId', 'operatorId', 'freezeDeviceId', 'workRound', 'sourceRecoveryEpoch')
    for name, value in zip(names, row[9:16]):
        require(str(proof[name]) == value, 'ARCHIVE_SOURCE_MISMATCH')
