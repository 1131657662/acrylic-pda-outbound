"""Read immutable command/decision/audit closure by canonical UUID; no current authority decisions."""
from uuid import UUID
from archive_proofs import parse
from safety import require

QUERY = '''SELECT JSON_OBJECT('id',BIN_TO_UUID(r.id),'commandType',r.command_type,
 'commandHex',HEX(r.canonical_command_bytes),'commandSha256',r.command_sha256,
 'responseHex',HEX(r.original_response_bytes),'responseSha256',r.original_response_sha256,
 'unsignedSha256',r.unsigned_receipt_sha256,'keyId',r.signature_key_id,'token',r.signed_receipt,
 'recordedAt',DATE_FORMAT(r.recorded_at,'%Y-%m-%dT%H:%i:%s.%fZ'),
 'scopeId',BIN_TO_UUID(r.scope_id),'warehouseId',BIN_TO_UUID(r.warehouse_id),'submissionId',BIN_TO_UUID(r.submission_id),
 'creatorDeviceId',BIN_TO_UUID(r.creator_device_id),'clientOrderId',BIN_TO_UUID(r.client_order_id),
 'authorizationId',BIN_TO_UUID(r.temporary_authorization_id),'originalOperatorId',BIN_TO_UUID(r.original_operator_id),
 'sourceRecoveryEpoch',CAST(r.source_recovery_epoch AS CHAR),'workRound',CAST(r.work_round AS CHAR),
 'canonicalOrderId',BIN_TO_UUID(r.original_canonical_order_id),'mappingId',BIN_TO_UUID(r.original_mapping_id),
 'actorId',BIN_TO_UUID(q.principal_id),'routeScope',q.route_scope,'commandId',q.idempotency_key,
 'requestSha256',q.request_sha256,'resourceId',BIN_TO_UUID(q.resource_id),
 'eventId',BIN_TO_UUID(e.id),'eventScopeId',BIN_TO_UUID(e.scope_id),'eventKind',e.decision_kind,
 'eventVersion',CAST(e.resulting_decision_version AS CHAR),'eventHex',HEX(e.context_bytes),
 'eventAt',DATE_FORMAT(e.recorded_at,'%Y-%m-%dT%H:%i:%s.%fZ'),
 'mappingScopeId',BIN_TO_UUID(m.scope_id),'mappingWarehouseId',BIN_TO_UUID(m.warehouse_id),
 'mappingOrderId',BIN_TO_UUID(m.canonical_order_id),'mappingVersion',CAST(m.mapping_version AS CHAR))
 FROM temporary_command_receipt r LEFT JOIN api_idempotency_request q ON q.id=r.api_request_id
 LEFT JOIN temporary_decision_event e ON e.receipt_id=r.id
 LEFT JOIN temporary_source_mapping m ON m.id=r.original_mapping_id WHERE r.id=UNHEX('{key}');'''


def read_record(db, receipt_id):
    key = UUID(receipt_id).hex
    require(str(UUID(receipt_id)) == receipt_id, 'COMMAND_ID_INVALID')
    rows = db.query(QUERY.format(key=key)).splitlines()
    require(len(rows) == 1, 'COMMAND_RECORD_MISSING')
    row = parse(rows[0])
    audits = db.query("SELECT JSON_OBJECT('actorId',BIN_TO_UUID(actor_id),'eventType',event_type,'details',details) "
                      "FROM operation_event WHERE entity_type='TEMPORARY_COMMAND' "
                      f"AND entity_id=UNHEX('{key}');").splitlines()
    require(len(audits) == 1, 'COMMAND_AUDIT_MISSING')
    row['audit'] = parse(audits[0])
    return row
