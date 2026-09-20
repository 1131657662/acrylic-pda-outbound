"""Check immutable audit source indices; expiry never invalidates old evidence."""
from safety import require


def event_index(value, row):
    names = ('id', 'originDeviceId', 'localSeq', 'operatorId')
    for key, expected in zip(names, (row[0], row[1], row[2], row[7])):
        require(str(value.get(key)) == expected, 'AUDIT_EVENT_INDEX_MISMATCH')
    require(int(row[2]) > 0 and int(row[8]) > 0, 'AUDIT_EVENT_INDEX_MISMATCH')
    if 'source' not in value:
        require(value.get('assignmentId') == row[6] and row[6] != 'NULL', 'AUDIT_EVENT_INDEX_MISMATCH')
        require('authorizationId' not in value and (len(row) == 9 or row[9:] == ['NULL', 'NULL']),
                'AUDIT_SOURCE_SHAPE_MISMATCH')
        return
    require(len(row) == 11 and row[6] == 'NULL' and 'assignmentId' not in value, 'AUDIT_SOURCE_SHAPE_MISMATCH')
    require(value.get('authorizationId') == row[9] and row[9] != 'NULL', 'AUDIT_TEMP_INDEX_MISMATCH')
    require(row[10] != 'NULL' and value['source'] == {
        'kind': 'TEMP', 'creatorDeviceId': row[1], 'clientOrderId': row[10]}, 'AUDIT_TEMP_INDEX_MISMATCH')


def provenance(db, version):
    query = '''SELECT COUNT(*) FROM pda_audit_event e
        LEFT JOIN order_assignment a ON a.id=e.assignment_id LEFT JOIN outbound_order o ON o.id=a.order_id
        WHERE e.assignment_id IS NOT NULL AND (a.id IS NULL OR o.id IS NULL
        OR e.origin_device_id<>a.device_id OR e.operator_id<>a.operator_id
        OR e.source_recovery_epoch<>a.recovery_epoch OR e.warehouse_id<>o.warehouse_id);'''
    require(db.query(query) == '0', 'AUDIT_PROVENANCE_MISMATCH')
    query = '''SELECT COUNT(*) FROM pda_audit_event e INNER JOIN submitted_event s
        ON s.id=e.id OR (s.origin_device_id=e.origin_device_id AND s.local_seq=e.local_seq)
        WHERE s.record_sha256<>e.record_sha256;'''
    require(db.query(query) == '0', 'AUDIT_SUBMITTED_EVENT_CONFLICT')
    if version == '32':
        temporary_provenance(db)


def temporary_provenance(db):
    query = '''SELECT COUNT(*) FROM pda_audit_event e
        LEFT JOIN temporary_authorization a ON a.id=e.temporary_authorization_id
        LEFT JOIN temporary_work_origin o ON o.creator_device_id=e.origin_device_id AND o.client_order_id=e.client_order_id
        WHERE e.assignment_id IS NULL AND (a.id IS NULL OR o.authorization_id IS NULL
        OR o.authorization_id<>a.id OR e.origin_device_id<>a.device_id OR e.operator_id<>a.user_id
        OR e.source_recovery_epoch<>a.recovery_epoch OR e.warehouse_id<>a.warehouse_id);'''
    require(db.query(query) == '0', 'AUDIT_TEMP_PROVENANCE_MISMATCH')
    query = '''SELECT COUNT(*) FROM temporary_work_origin o LEFT JOIN temporary_authorization a
        ON a.id=o.authorization_id WHERE a.id IS NULL OR o.creator_device_id<>a.device_id;'''
    require(db.query(query) == '0', 'TEMP_ORIGIN_BINDING_MISMATCH')
