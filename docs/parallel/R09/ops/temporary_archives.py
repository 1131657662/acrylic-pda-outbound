"""TEMP archive V2 restores three immutable files plus original confirmation/approval SQL records."""
from uuid import UUID
from archive_proofs import parse, canonical
from command_archive_proofs import signed
from temporary_archive_commands import confirmation
from temporary_archive_products import success_products
from safety import require

FIELDS = set(('archiveVersion submissionId orderId assignmentId authorizationId warehouseId operatorId freezeDeviceId workRound '
              'sourceRecoveryEpoch recoveryEpoch payloadSha256 payloadBytes receiptSha256 receiptBytes commandReceiptId '
              'commandResponseSha256 commandBytes mappingVersion planSha256 completedAt storagePolicy localDeletionAllowed '
              'source archivedAt retainUntil signedArchive').split())

SOURCE = '''SELECT JSON_OBJECT('sourceOrderId',BIN_TO_UUID(c.source_order_id),'orderId',BIN_TO_UUID(c.canonical_order_id),
 'creatorDeviceId',BIN_TO_UUID(c.creator_device_id),'clientOrderId',BIN_TO_UUID(c.client_order_id),
 'authorizationId',BIN_TO_UUID(s.temporary_authorization_id),'originAuthorizationId',BIN_TO_UUID(t.authorization_id),
 'authDeviceId',BIN_TO_UUID(a.device_id),'authUserId',BIN_TO_UUID(a.user_id),'authWarehouseId',BIN_TO_UUID(a.warehouse_id),
 'authRecoveryEpoch',CAST(a.recovery_epoch AS CHAR),'receivedAt',DATE_FORMAT(p.received_at,'%Y-%m-%dT%H:%i:%s.%fZ'))
 FROM submission s JOIN submission_scope c ON c.id=s.scope_id JOIN submission_payload p ON p.submission_id=s.id
 LEFT JOIN temporary_work_origin t ON t.creator_device_id=c.creator_device_id AND t.client_order_id=c.client_order_id
 LEFT JOIN temporary_authorization a ON a.id=s.temporary_authorization_id WHERE s.id=UNHEX('{key}');'''


def verify_temporary(db, files, case):
    proof, row, body, receipt = (case[name] for name in ('proof', 'row', 'body', 'receipt'))
    require(set(proof) == FIELDS, 'TEMP_ARCHIVE_FIELDS')
    require(proof['mappingVersion'] == proof['workRound'] == '1' and proof['assignmentId'] is None, 'TEMP_ARCHIVE_SCOPE_UNSUPPORTED')
    key = UUID(proof['submissionId']).hex
    records = db.query(SOURCE.format(key=key)).splitlines()
    require(len(records) == 1, 'TEMP_ARCHIVE_SOURCE_MISSING')
    source = parse(records[0]); source_identity(proof, source, row)
    require(body['source'] == proof['source'] and body['assignmentId'] is None and body['continuationId'] is None, 'TEMP_ARCHIVE_BODY_SOURCE')
    for name in ('submissionId', 'authorizationId', 'freezeDeviceId', 'workRound', 'sourceRecoveryEpoch', 'planSha256'):
        require(body[name] == proof[name], 'TEMP_ARCHIVE_BODY_CONTEXT')
    signed(receipt, case['keys'], 'acrylic-pda-receipt')
    success_context(proof, body, receipt)
    success_products(body, receipt)
    case['receivedAt'] = source['receivedAt']
    return confirmation(db, files, case)


def source_identity(proof, source, row):
    require(row[9] == row[10] == 'NULL' and source['sourceOrderId'] is None, 'TEMP_ARCHIVE_FORMAL_SOURCE')
    expected = {'kind': 'TEMP', 'creatorDeviceId': source['creatorDeviceId'], 'clientOrderId': source['clientOrderId']}
    require(proof['source'] == expected and source['orderId'] == proof['orderId'], 'TEMP_ARCHIVE_SOURCE')
    require(source['authorizationId'] == source['originAuthorizationId'] == proof['authorizationId'], 'TEMP_ARCHIVE_AUTHORIZATION')
    names = ('warehouseId', 'operatorId', 'freezeDeviceId', 'workRound', 'sourceRecoveryEpoch')
    require(all(proof[name] == value for name, value in zip(names, row[11:16])), 'TEMP_ARCHIVE_OWNER')
    require(proof['freezeDeviceId'] == source['authDeviceId'] == source['creatorDeviceId'] and
            proof['operatorId'] == source['authUserId'] and proof['warehouseId'] == source['authWarehouseId'] and
            proof['sourceRecoveryEpoch'] == source['authRecoveryEpoch'], 'TEMP_ARCHIVE_ORIGINAL_IDENTITY')


def success_context(proof, body, receipt):
    for name in ('submissionId', 'orderId', 'freezeDeviceId', 'workRound', 'source', 'payloadSha256', 'completedAt'):
        require(canonical(receipt[name]) == canonical(proof[name]), 'TEMP_ARCHIVE_SUCCESS_SOURCE')
    require(receipt['issuedRecoveryEpoch'] == proof['sourceRecoveryEpoch'] and receipt['captureHighWatermark'] == body['captureHighWatermark'], 'TEMP_ARCHIVE_SUCCESS_CONTEXT')
    require(receipt['orderStatus'] == 'COMPLETED' and receipt['resultVersion'] == '1' and
            type(receipt['receiptVersion']) is int and receipt['receiptVersion'] == 1, 'TEMP_ARCHIVE_SUCCESS_VERSION')
    require(receipt['inventoryDeducted'] is body['autoDeductInventory'] and receipt['stockReleased'] is False and
            receipt['mayResumeScanning'] is False, 'TEMP_ARCHIVE_SUCCESS_AUTHORITY')
