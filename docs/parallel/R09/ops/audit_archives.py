"""Validate original formal/TEMP events and signed continuous archive coverage."""
from uuid import UUID
from archive_proofs import parse, canonical, content, verify_proof
from safety import require, digest


def events(db):
    from audit_origins import event_index, provenance
    version = db.query('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history;')
    extra = ',BIN_TO_UUID(temporary_authorization_id),BIN_TO_UUID(client_order_id)' if version in ('32', '33', '34') else ''
    query = '''SELECT BIN_TO_UUID(id),BIN_TO_UUID(origin_device_id),local_seq,record_sha256,HEX(payload),
        BIN_TO_UUID(warehouse_id),BIN_TO_UUID(assignment_id),BIN_TO_UUID(operator_id),source_recovery_epoch'''
    query += extra + ' FROM pda_audit_event ORDER BY origin_device_id,local_seq;'
    result = {}
    for line in db.query(query).splitlines():
        row = line.split('\t')
        value = parse(bytes.fromhex(row[4]))
        require(digest(canonical(value).encode('utf-8')) == row[3], 'AUDIT_EVENT_HASH_MISMATCH')
        event_index(value, row)
        result[(row[1], int(row[2]))] = (value, row[5])
    provenance(db, version)
    return result


def checkpoints(db, original):
    result = {}
    for line in db.query('SELECT recovery_epoch,BIN_TO_UUID(origin_device_id),through_seq FROM pda_audit_checkpoint;').splitlines():
        epoch, device, through = line.split('\t')
        sequence = sorted(seq for dev, seq in original if dev == device and seq <= int(through))
        require(len(sequence) == int(through) and (not sequence or sequence[0] == 1 and sequence[-1] == len(sequence)),
                'AUDIT_CHECKPOINT_GAP')
        result[(epoch, device)] = int(through)
    return result


def verify_archives(db, files, keys):
    original = events(db)
    confirmed = checkpoints(db, original)
    rows = db.query('SELECT recovery_epoch,BIN_TO_UUID(origin_device_id),after_seq,through_seq,payload_sha256,'
                    'HEX(proof_text) FROM pda_audit_archive ORDER BY recovery_epoch,origin_device_id,after_seq;').splitlines()
    ends, covered = {}, 0
    for line in rows:
        epoch, device, after, through, checksum, encoded = line.split('\t')
        proof = parse(bytes.fromhex(encoded))
        verify_proof(proof, keys, 'acrylic-pda-audit-archive')
        for name, value in zip(('recoveryEpoch', 'originDeviceId', 'afterSeq', 'throughSeq', 'payloadSha256'),
                               (epoch, device, after, through, checksum)):
            require(proof[name] == value, 'AUDIT_ARCHIVE_INDEX_MISMATCH')
        require(int(after) == ends.get((epoch, device), 0) and 0 < int(through) - int(after) <= 200, 'AUDIT_ARCHIVE_GAP')
        require(int(through) <= confirmed.get((epoch, device), -1), 'AUDIT_ARCHIVE_CHECKPOINT_MISSING')
        body = content(files, checksum, proof['byteCount'])
        require(int(proof['byteCount']) <= 512 * 1024, 'AUDIT_ARCHIVE_TOO_LARGE')
        verify_events(body, proof, original)
        ends[(epoch, device)] = int(through)
        covered += len(body['events'])
    return {'audit_archives_verified': len(rows), 'archived_audit_events_verified': covered,
            'audit_event_rows_verified': len(original)}


def verify_events(body, proof, original):
    for name in ('originDeviceId', 'afterSeq', 'throughSeq'):
        require(body[name] == proof[name], 'AUDIT_ARCHIVE_BODY_MISMATCH')
    require(len(body['events']) == int(proof['throughSeq']) - int(proof['afterSeq']), 'AUDIT_ARCHIVE_EVENT_GAP')
    for seq, event in enumerate(body['events'], int(proof['afterSeq']) + 1):
        require(event['originDeviceId'] == proof['originDeviceId'] and int(event['localSeq']) == seq, 'AUDIT_ARCHIVE_EVENT_GAP')
        require(original.get((proof['originDeviceId'], seq)) == (event, proof['warehouseId']), 'AUDIT_ARCHIVE_EVENT_MISMATCH')
