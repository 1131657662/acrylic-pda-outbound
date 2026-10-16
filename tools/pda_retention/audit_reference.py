"""Verify retained, unrelated audit namespaces without deleting their facts."""
import calendar
from collections import defaultdict

from .codec import canonical, digest, parse, require
from .proof import instant
from .signatures import verified

AUDIT = {'local_audit_archive', 'local_audit_checkpoint'}


def unrelated_audits(rows, owned, context):
    groups = defaultdict(list)
    for row in rows['local_audit_archive']:
        groups[(row['recoveryEpoch'], row['originDeviceId'])].append(row)
    checkpoints = {(r['recoveryEpoch'], r['originDeviceId']): r
                   for r in rows['local_audit_checkpoint']}
    require(set(checkpoints) == set(groups), 'Audit checkpoint namespace mismatch')
    target_events = {r['id'] for r in owned['work_event']}
    for identity, archives in groups.items():
        require(len(archives) <= 1024, 'Audit archive resource limit')
        through = 0
        for row in sorted(archives, key=lambda r: r['afterSeq']):
            require(row['afterSeq'] == through, 'Audit prefix gap')
            proof = confirmation(row, context)
            payload = event_payload(rows, row, target_events)
            require(digest(canonical(payload)) == row['payloadSha256'], 'Audit event digest mismatch')
            require(str(len(canonical(payload))) == proof['byteCount'], 'Audit event byte count mismatch')
            request(row, rows['local_audit_archive'], context)
            warehouse(rows, payload['events'], proof['warehouseId'])
            through = row['throughSeq']
        checkpoint = checkpoints[identity]
        require(checkpoint['pendingJson'] is None and checkpoint['acknowledgedThrough'] == through,
                'Audit checkpoint pending or incomplete')


def confirmation(row, context):
    require(row['proofJson'] is not None and row['signingKeyJson'] is not None, 'Pending audit protected')
    proof, key = parse(row['proofJson']), parse(row['signingKeyJson'])
    require(verified(proof, ('signedArchive', 'acrylic-pda-audit-archive'), key)
            == context.get('auditKeySha256'), 'Pinned audit key mismatch')
    require(0 <= row['afterSeq'] < row['throughSeq'] <= row['afterSeq'] + 200, 'Audit range invalid')
    expected = dict(archiveVersion=1, recoveryEpoch=row['recoveryEpoch'],
                    originDeviceId=row['originDeviceId'], afterSeq=str(row['afterSeq']),
                    throughSeq=str(row['throughSeq']), payloadSha256=row['payloadSha256'],
                    storagePolicy='SERVER_DURABLE_FILES_V1', localDeletionAllowed=False)
    for field, value in expected.items():
        require(type(proof[field]) is type(value) and proof[field] == value, 'Audit binding: ' + field)
    require(isinstance(proof['byteCount'], str) and proof['byteCount'].isdigit() and
            0 < int(proof['byteCount']) <= 512 * 1024, 'Audit byte count invalid')
    retained(proof, context['now'])
    return proof


def retained(proof, now):
    at, until = instant(proof['archivedAt']), instant(proof['retainUntil'])
    year = int(proof['archivedAt'][:4]) + 5
    anniversary = str(year) + proof['archivedAt'][4:]
    if proof['archivedAt'][5:10] == '02-29' and not calendar.isleap(year):
        anniversary = str(year) + '-02-28' + proof['archivedAt'][10:]
    require(at <= instant(now) < until and until >= instant(anniversary), 'Audit retention invalid')


def request(row, archives, context):
    value = parse(row['requestJson'])
    require(row['restoring'] in (0, 1), 'Audit restoring flag invalid')
    if row['restoring'] == 0:
        expected = {k: row[k] for k in ('originDeviceId', 'payloadSha256')}
        expected.update(afterSeq=str(row['afterSeq']), throughSeq=str(row['throughSeq']))
        require(value == expected, 'Audit original request mismatch')
        return
    require(set(value) == {'archive'}, 'Audit restore envelope mismatch')
    original = value['archive']
    prior = [r for r in archives if r['proofJson'] is not None and parse(r['proofJson']) == original]
    require(len(prior) == 1 and prior[0]['recoveryEpoch'].isdigit() and row['recoveryEpoch'].isdigit() and
            int(prior[0]['recoveryEpoch']) < int(row['recoveryEpoch']),
            'Audit restore original proof missing')
    confirmation(prior[0], context)
    current = parse(row['proofJson'])
    for field in ('originDeviceId', 'warehouseId', 'afterSeq', 'throughSeq', 'payloadSha256', 'byteCount'):
        require(current[field] == original[field], 'Audit restore binding mismatch: ' + field)
    require(instant(original['archivedAt']) <= instant(current['archivedAt']), 'Audit restore time reversal')


def event_payload(rows, row, target_events):
    events = sorted((r for r in rows['work_event'] if r['originDeviceId'] == row['originDeviceId'] and
                     row['afterSeq'] < r['localSeq'] <= row['throughSeq']), key=lambda r: r['localSeq'])
    require([r['localSeq'] for r in events] == list(range(row['afterSeq'] + 1, row['throughSeq'] + 1)),
            'Audit event prefix incomplete')
    require(not target_events.intersection(r['id'] for r in events), 'Target audit reference protected')
    require(all(r['originAssignmentId'] in ('', r['assignmentId']) for r in events),
            'Audit event assignment mismatch')
    devices = [r for r in rows['work_device'] if r['deviceId'] == row['originDeviceId']]
    require(len(devices) == 1 and devices[0]['nextEventSeq'] > row['throughSeq'], 'Audit device sequence invalid')
    return dict(originDeviceId=row['originDeviceId'], afterSeq=str(row['afterSeq']),
                throughSeq=str(row['throughSeq']), events=[wire(r) for r in events])


def wire(event):
    result = {k: event[k] for k in ('id', 'captureId', 'originDeviceId', 'operatorId', 'type', 'detail', 'boot')}
    result['assignmentId'] = event['originAssignmentId'] or event['assignmentId']
    result.update({k: str(event[k]) for k in ('localSeq', 'wallMs', 'elapsedMs')})
    return result


def warehouse(rows, events, warehouse_id):
    orders = {r['assignmentId']: r for r in rows['work_order']}
    prepared = {r['assignmentId']: r for r in rows['prepared_order']}
    captures = {r['id']: r for r in rows['work_capture']}
    for event in events:
        assignment = event['assignmentId']
        require(assignment in orders and assignment in prepared, 'Audit order reference missing')
        order, source = orders[assignment], prepared[assignment]
        require(order['orderId'] == source['orderId'] and
                source['operatorId'] == event['operatorId'] and source['deviceId'] == event['originDeviceId'],
                'Audit order identity mismatch')
        if event['captureId'] is None:
            continue
        capture = captures.get(event['captureId'])
        require(capture is not None and capture['assignmentId'] == assignment and
                capture['originAssignmentId'] in ('', assignment) and capture['orderId'] == order['orderId'] and
                capture['warehouseId'] == warehouse_id and capture['operatorId'] == event['operatorId'] and
                capture['originDeviceId'] == event['originDeviceId'], 'Audit capture identity mismatch')
