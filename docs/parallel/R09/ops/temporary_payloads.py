"""Bind accepted V33 TEMP bytes and immutable indices to their original control."""
from uuid import UUID
from archive_proofs import parse, canonical
from safety import child, regular, require, digest
from temporary_submissions import source


def payload_record(db, key):
    rows = db.query("SELECT JSON_OBJECT('hash',payload_sha256,'size',byte_count,'file',file_key,"
        "'version',schema_version,'count',expected_count,'weight',expected_weight_g,'high',capture_high_watermark) "
        f"FROM submission_payload WHERE submission_id=UNHEX('{key}');").splitlines()
    return parse(rows[0]) if rows else None


def body_control(row, body, payload):
    expected = {'schemaVersion': 2, 'submissionId': row['id'], 'source': source(row), 'assignmentId': None,
                'continuationId': None, 'authorizationId': row['auth'], 'freezeDeviceId': row['freeze'],
                'workRound': str(row['round']), 'sourceRecoveryEpoch': str(row['epoch'])}
    require(all(k in body and body[k] == v for k, v in expected.items()), 'TEMP_PAYLOAD_CONTROL_MISMATCH')
    require(payload['version'] == 2 and [body.get(k) for k in ('expectedCount', 'expectedWeightGrams', 'captureHighWatermark')]
            == [str(payload[k]) for k in ('count', 'weight', 'high')], 'TEMP_PAYLOAD_SUMMARY_MISMATCH')
    manifest = parse(row['manifest'])
    require(body.get('authorizationEpoch') == manifest['identity']['authorizationEpoch'] and
            body.get('inventorySnapshotId') == manifest['inventorySnapshotId'] and
            body.get('policyVersion') == manifest['policyVersion'], 'TEMP_PAYLOAD_BASELINE_MISMATCH')
    plan = body['temporaryOrder']
    require(digest(canonical(plan).encode('utf-8')) == body.get('planSha256'), 'TEMP_PLAN_HASH_MISMATCH')
    require(plan.get('clientOrderId') == row['client'] and plan.get('creatorDeviceId') == row['creator'] and
            plan.get('authorizationId') == row['auth'] and plan.get('revision') == body.get('orderRevision'), 'TEMP_PLAN_SOURCE_MISMATCH')


def indices(db, row, body):
    key = UUID(row['id']).hex
    captures = [line.split('\t') for line in db.query('SELECT BIN_TO_UUID(id),BIN_TO_UUID(order_id),'
        'BIN_TO_UUID(origin_device_id),device_capture_seq,order_scan_seq,record_sha256 FROM submitted_capture '
        f"WHERE submission_id=UNHEX('{key}') ORDER BY order_scan_seq;").splitlines()]
    expected = [[c['id'], row['order'], c['originDeviceId'], c['deviceCaptureSeq'], c['orderScanSeq'],
                 digest(canonical(c).encode('utf-8'))] for c in body['captures']]
    require(captures == expected, 'TEMP_CAPTURE_INDEX_MISMATCH')
    events = [line.split('\t') for line in db.query('SELECT BIN_TO_UUID(id),BIN_TO_UUID(origin_device_id),'
        f"local_seq,record_sha256 FROM submitted_event WHERE submission_id=UNHEX('{key}') ORDER BY local_seq;").splitlines()]
    expected = [[e['id'], e['originDeviceId'], e['localSeq'], digest(canonical(e).encode('utf-8'))] for e in body['events']]
    require(events == expected, 'TEMP_EVENT_INDEX_MISMATCH')


def reservations(db, row, body):
    key = UUID(row['id']).hex
    owned = [line.split('\t') for line in db.query('SELECT b.barcode,c.dispatch_cycle,c.state,BIN_TO_UUID(c.order_id) '
        'FROM bale_commitment c INNER JOIN bale b ON b.id=c.bale_id '
        f"WHERE c.submission_id=UNHEX('{key}') ORDER BY b.barcode;").splitlines()]
    conflict_codes = {'DISPATCH_CYCLE_CONFLICT', 'INVENTORY_UNAVAILABLE', 'BALE_ALREADY_COMMITTED', 'INVENTORY_BASELINE_CHANGED'}
    conflicts = {i['barcode'] for i in (row['issues'] or []) if i.get('code') in conflict_codes}
    active = {s['barcode']: s for s in body['scans'] if s['status'] == 'ACTIVE'}
    require(conflicts <= set(active), 'TEMP_RESERVATION_ISSUE_MISMATCH')
    expected = [[code, scan['expectedDispatchCycle'], 'REVIEW_RESERVED', row['order']]
                for code, scan in sorted(active.items()) if code not in conflicts]
    # V33 abort clears issues_json; the signed receipt never authorizes release.
    # Only retained ownership can be verified here, not a pre-abort historical set.
    valid = all(item in expected for item in owned) if row['status'] == 'ABORTED' else owned == expected
    require(valid, 'TEMP_RESERVATION_MISMATCH')
    require(len(active) == int(body['expectedCount']), 'TEMP_PAYLOAD_SUMMARY_MISMATCH')


def verify_payload(db, files, row, payload):
    from temporary_plans import verify_plan
    path = regular(child(files, payload['file']))
    raw = path.read_bytes()
    require(len(raw) == payload['size'] and digest(raw) == payload['hash'], 'TEMP_PAYLOAD_FILE_MISMATCH')
    body = parse(raw)
    require(canonical(body).encode('utf-8') == raw, 'TEMP_PAYLOAD_NOT_CANONICAL')
    body_control(row, body, payload)
    verify_plan(db, row, body)
    indices(db, row, body)
    reservations(db, row, body)
