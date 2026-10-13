"""Recompute the complete collection prefix and verify its original device closure signature."""
import base64
import hashlib
import json
from datetime import datetime
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from .integrity import parse
from .facts import integer, records

PURPOSE = 'INDEPENDENT_CAPTURE_CLOSED_V1'
EVENT = 'INDEPENDENT_CLOSED'


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf-8')


def expected(facts, source, voids, event, reason, ended_at):
    task, credential = source['task'], source['credential']
    if any(row['state'] in {'RECEIVED', 'WAITING_CONFIRM', 'CONFIRM_REQUESTED'} or
           row['state'] in {'INVALID', 'BUSINESS_REJECTED'} and row['failureAckAt'] is None for row in facts['captures']):
        raise ValueError('Closed collection has unresolved or unacknowledged input')
    prefix = facts['events'][:-1]
    active = [row for row in facts['scans'] if row['status'] == 'ACTIVE']
    shortfall = integer(task, 'plannedTotal') - len(active)
    if shortfall < 0 or shortfall and not reason.strip() or len(reason) > 500:
        raise ValueError('Closed shortfall requires its original reason')
    original = {'source': source, 'captures': facts['captures'], 'scans': facts['scans'],
                'lines': facts['lines'], 'events': prefix, 'voids': voids}
    return {'purpose': PURPOSE, 'taskId': task['id'], 'operatorId': task['operatorId'], 'deviceId': task['deviceId'],
            'warehouseId': task['warehouseId'], 'credentialId': credential['credentialId'],
            'credentialSha256': credential['credentialSha256'], 'skuSha256': task['skuSha256'],
            'commandSha256': task['commandSha256'], 'recoveryEpoch': task['recoveryEpoch'],
            'inputHighWater': str(len(facts['captures'])), 'eventHighWater': str(len(prefix)),
            'plannedTotal': task['plannedTotal'], 'active': str(len(active)),
            'void': str(sum(row['status'] == 'VOID' for row in facts['scans'])),
            'weightGrams': str(sum(integer(row, 'weightGrams') for row in active)),
            'shortfall': str(shortfall), 'shortfallReason': reason, 'closedAt': ended_at,
            'closedWall': event['wall'], 'closedElapsed': event['elapsed'], 'closedBoot': event['boot'],
            'factsSha256': hashlib.sha256(canonical(original)).hexdigest(),
            'pendingReconciliation': True, 'businessProcessed': False, 'localDeletionAllowed': False}


def verify(files, facts, source, ended_at):
    endings = [row for row in facts['events'] if row['type'] == EVENT]
    if facts['order']['state'] != 'SEALED':
        if endings:
            raise ValueError('Closure on an unsealed collection')
        return None
    if len(endings) != 1 or facts['events'][-1] is not endings[0]:
        raise ValueError('Closure must be the unique final original event')
    event = endings[0]; envelope = parse(event['detail'].encode())
    if set(envelope) != {'purpose', 'body', 'bodySha256', 'devicePublicKey', 'signature'} or envelope['purpose'] != PURPOSE:
        raise ValueError('Unexpected closure fields')
    raw = envelope['body'].encode('utf-8'); body = parse(raw)
    if datetime.fromisoformat(body['closedAt']) != ended_at or integer(event, 'elapsed') < max(
            (integer(row, 'elapsed') for row in facts['events'][:-1]), default=0):
        raise ValueError('Closure does not follow its complete original prefix')
    value = expected(facts, source, records(files['independent-voids.ndjson']), event, body['shortfallReason'], body['closedAt'])
    if body != value or raw != canonical(value) or hashlib.sha256(raw).hexdigest() != envelope['bodySha256']:
        raise ValueError('Closure does not bind complete original facts')
    der = base64.b64decode(envelope['devicePublicKey'], validate=True)
    if der != files['device-public-key.der'] or hashlib.sha256(der).hexdigest() != source['credential']['deviceKeySha256']:
        raise ValueError('Closure uses another device key')
    key = serialization.load_der_public_key(der)
    if not isinstance(key, ec.EllipticCurvePublicKey) or not isinstance(key.curve, ec.SECP256R1):
        raise ValueError('Closure device key is not P256')
    key.verify(base64.b64decode(envelope['signature'], validate=True), PURPOSE.encode() + b'\n' + raw, ec.ECDSA(hashes.SHA256()))
    return body
