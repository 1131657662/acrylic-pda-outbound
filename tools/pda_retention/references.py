"""Fail-closed reference closure for an initial FORMAL batch without target guards."""
from .catalog import GLOBAL, OWNED, validate
from .audit_reference import AUDIT, unrelated_audits
from .codec import canonical, parse, require
from .guard_reference import unrelated_guards
from .proof import check
from .store import facts


def selection(db, context):
    validate(db)
    rows = {t: [parse(raw) for raw in values] for t, values in facts(db).items()}
    local = one([r for r in rows['local_submission'] if r['id'] == context['submissionId']])
    require(local['state'] == 'SYNCED' and local['autoRetry'] == 0 and
            local['currentMarker'] == 1, 'Submission is not final and inactive')
    archive = one([r for r in rows['local_submission_archive'] if r['submissionId'] == local['id']])
    body, confirmation = check(local, archive, context)
    owned = owned_rows(rows, local)
    formal(owned, body, confirmation)
    closure(rows, owned, dict(body=body, spec=context))
    return {t: sorted(canonical(r).decode() for r in owned[t]) for t in OWNED}


def one(rows):
    require(len(rows) == 1, 'Exactly one matching record required')
    return rows[0]


def owned_rows(rows, local):
    selected = {}
    for table in OWNED:
        field = 'submissionId' if table == 'local_submission_archive' else 'assignmentId'
        value = local['id'] if field == 'submissionId' else local['assignmentId']
        selected[table] = [r for r in rows[table] if r[field] == value]
    require(len(selected['local_submission']) == 1, 'Multiple submission history protected')
    return selected


def formal(owned, body, archive):
    prepared, work = one(owned['prepared_order']), one(owned['work_order'])
    require(work['state'] == 'FROZEN', 'In-flight order protected')
    expected = dict(orderId=archive['orderId'], operatorId=archive['operatorId'],
                    deviceId=archive['freezeDeviceId'])
    require(all(prepared[k] == v for k, v in expected.items()), 'Prepared identity mismatch')
    require(work['orderId'] == archive['orderId'], 'Work order identity mismatch')
    require(parse(prepared['signingKeyJson']) == parse(one(owned['local_submission_archive'])['signingKeyJson']),
            'Prepared signing key mismatch')
    require(str(work['actualCount']) == body['expectedCount'] and
            str(work['weightGrams']) == body['expectedWeightGrams'], 'Work totals mismatch')
    for table, field in [('work_capture', 'captures'), ('work_scan', 'scans'), ('work_event', 'events')]:
        frozen_rows(owned[table], body[field])
    require(all(r['state'] == 'COMMITTED' for r in owned['work_capture']), 'Unresolved input protected')
    require(all(r['status'] == 'ACTIVE' for r in owned['work_scan']), 'Disposition history protected')
    require(all(r.get('originAssignmentId') in ('', body['assignmentId'])
                for r in owned['work_capture'] + owned['work_event']),
            'Cross-assignment history protected')


def frozen_rows(actual, frozen):
    key = 'captureId' if frozen and 'captureId' in frozen[0] and 'id' not in frozen[0] else 'id'
    indexed = {r[key]: r for r in actual}
    require(len(indexed) == len(actual) == len(frozen), 'Frozen record set differs')
    for record in frozen:
        require(record[key] in indexed, 'Frozen record missing')
        row = indexed[record[key]]
        for field, value in record.items():
            require(field in row, 'Unknown frozen field')
            expected = int(value) if isinstance(row[field], int) and isinstance(value, str) else value
            require(row[field] == expected, 'Frozen record differs: ' + field)


def closure(rows, owned, context):
    body = context['body']
    control = one(rows['local_recovery_control'])
    require(control['mode'] == 'NORMAL' and control['recoverySessionId'] is None and
            control['observedServerEpoch'] == body['sourceRecoveryEpoch'], 'Recovery reference protected')
    tokens = {body['assignmentId'], body['submissionId'], body['source']['sourceOrderId']}
    tokens.update(r['id'] for r in body['captures'])
    tokens.update(r['id'] for r in body['events'])
    tokens.update(r['barcode'] for r in body['scans'])
    unrelated_audits(rows, owned, context['spec'])
    for table, records in rows.items():
        if table in AUDIT:
            require(not any(references(r, tokens) for r in records), 'Target audit envelope protected')
            continue
        if table in GLOBAL:
            continue
        if table == 'work_guard':
            require(not any(references(r, tokens) for r in records), 'Effective guard protects target order')
            unrelated_guards(rows)
            continue
        if table not in OWNED:
            require(not records, 'Protected or opaque reference table: ' + table)
            continue
        remainder = [r for r in records if r not in owned[table]]
        require(not any(references(r, tokens) for r in remainder), 'External reference: ' + table)


def references(value, tokens):
    if isinstance(value, dict):
        return any(references(v, tokens) for v in value.values())
    if isinstance(value, list):
        return any(references(v, tokens) for v in value)
    if isinstance(value, str):
        if value in tokens:
            return True
        if value.startswith(('{', '[')):
            return references(parse(value), tokens)
        return any(token in value for token in tokens)
    return False
