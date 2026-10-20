"""Read-only first-round TEMP original files, with externally verified scope."""
from datetime import datetime
import hashlib
import re
import uuid

from .facts import integer, ordered, unique
from .integrity import parse
from .receipt import FIELDS
from .receipt_signature import same, verify_signature

CONTEXT_IDS = {'submissionId', 'creatorDeviceId', 'clientOrderId', 'authorizationId',
               'operatorId', 'warehouseId', 'orderId'}
MAX_PAYLOAD = 64 * 1024 * 1024
MAX_RESPONSE = 1024 * 1024


def digest(data):
    return hashlib.sha256(data).hexdigest()


def identifier(value):
    if not isinstance(value, str) or str(uuid.UUID(value)) != value:
        raise ValueError('Original scope requires canonical UUIDs')


def trusted_context(payload, context):
    if not isinstance(context, dict) or set(context) != CONTEXT_IDS | {'payloadSha256'}:
        raise ValueError('Externally verified original TEMP context is required')
    for field in CONTEXT_IDS:
        identifier(context[field])
    if not isinstance(context['payloadSha256'], str) or not re.fullmatch('[0-9a-f]{64}', context['payloadSha256']):
        raise ValueError('Invalid externally pinned payload digest')
    same(digest(payload), context['payloadSha256'])


def original_scope(body, context):
    source = {name: context[name] for name in ['creatorDeviceId', 'clientOrderId']}
    same(body['source'], {'kind': 'TEMP', **source})
    for field, expected in [('submissionId', context['submissionId']), ('authorizationId', context['authorizationId']),
                            ('freezeDeviceId', context['creatorDeviceId']), ('workRound', '1'),
                            ('assignmentId', None), ('continuationId', None), ('dispositions', [])]:
        same(body[field], expected)
    if {'temporaryHandover', 'temporaryContinuation'} & body.keys():
        raise ValueError('Only first-round TEMP originals are supported')
    for field, expected in {**source, 'authorizationId': context['authorizationId']}.items():
        same(body['temporaryOrder'][field], expected)


def success_context(body, receipt, context):
    optional = {'localDeletionAllowed'} if 'localDeletionAllowed' in receipt else set()
    if set(receipt) != FIELDS | optional:
        raise ValueError('Unsupported TEMP success receipt fields')
    if optional:
        same(receipt['localDeletionAllowed'], False)
    expected = {'submissionId': context['submissionId'], 'orderId': context['orderId'], 'status': 'SUCCEEDED',
                'payloadSha256': context['payloadSha256'], 'freezeDeviceId': body['freezeDeviceId'],
                'workRound': '1', 'issuedRecoveryEpoch': body['sourceRecoveryEpoch'], 'source': body['source'],
                'orderStatus': 'COMPLETED', 'actualCount': body['expectedCount'],
                'actualWeightGrams': body['expectedWeightGrams'], 'inventoryDeducted': body['autoDeductInventory'],
                'resultVersion': '1', 'receiptVersion': 1, 'captureHighWatermark': body['captureHighWatermark'],
                'mayResumeScanning': False, 'stockReleased': False}
    same({name: receipt[name] for name in expected}, expected)
    if type(body['autoDeductInventory']) is not bool:
        raise ValueError('Inventory mode must be boolean')
    for field in ['sourceRecoveryEpoch', 'captureHighWatermark', 'expectedCount', 'expectedWeightGrams']:
        if integer(body, field) < 1:
            raise ValueError('Historical success must contain positive original facts')
    if not isinstance(receipt['orderNo'], str) or not receipt['orderNo'].strip():
        raise ValueError('Missing historical order number')
    if not isinstance(receipt['completedAt'], str) or datetime.fromisoformat(receipt['completedAt']).tzinfo is None:
        raise ValueError('Missing historical completion time zone')


def original_captures(body, context):
    captures = body['captures']
    if not isinstance(captures, list) or not 1 <= len(captures) <= 100_000:
        raise ValueError('Invalid original capture count')
    unique(captures, 'id'); ordered(captures, 'orderScanSeq')
    same([row['orderScanSeq'] for row in captures], [str(index) for index in range(1, len(captures) + 1)])
    same(body['captureHighWatermark'], str(len(captures)))
    expected = {'operatorId': context['operatorId'], 'originDeviceId': context['creatorDeviceId'],
                'authorizationId': context['authorizationId'], 'clientOrderId': context['clientOrderId']}
    for row in captures:
        identifier(row['id'])
        same({name: row[name] for name in expected}, expected)
        if row['state'] not in {'COMMITTED', 'INVALID', 'BUSINESS_REJECTED', 'DISCARDED'}:
            raise ValueError('Historical success cannot contain pending captures')
    return {row['id']: row for row in captures}


def effective_scans(body, captures):
    scans = body['scans']
    if not isinstance(scans, list) or not 1 <= len(scans) <= 100_000:
        raise ValueError('Invalid original scan count')
    unique(scans, 'captureId'); ordered(scans, 'orderScanSeq'); ordered(scans, 'localSeq')
    for row in scans:
        capture = captures[row['captureId']]
        same(capture['state'], 'COMMITTED')
        for field in ['orderScanSeq', 'barcode', 'expectedDispatchCycle']:
            same(row[field], capture[field])
        identifier(row['baleId'])
        if row['status'] not in {'ACTIVE', 'VOID'} or integer(row, 'weightGrams') < 1 or integer(row, 'expectedDispatchCycle') < 1:
            raise ValueError('Invalid original scan status, cycle or integer grams')
    same(sorted(row['captureId'] for row in scans),
         sorted(key for key, row in captures.items() if row['state'] == 'COMMITTED'))
    active = [row for row in scans if row['status'] == 'ACTIVE']
    if not active or len(active) != integer(body, 'expectedCount'):
        raise ValueError('Effective count differs from frozen success')
    if sum(integer(row, 'weightGrams') for row in active) != integer(body, 'expectedWeightGrams'):
        raise ValueError('Effective integer grams differ from frozen success')
    return active


def products(body, active, receipt):
    plan = body['temporaryOrder']['lines']
    if not isinstance(plan, list) or not 1 <= len(plan) <= 500:
        raise ValueError('Invalid TEMP plan size')
    unique(plan, 'deliveryCode')
    expected = {row['deliveryCode']: [0, 0] for row in plan}
    for row in plan:
        if not re.fullmatch('[0-9]{3}', row['deliveryCode']) or not 1 <= integer(row, 'plannedCount') <= 2**31 - 1:
            raise ValueError('TEMP plan must contain original codes and bounded positive counts')
    if any(row['deliveryCode'] not in expected for row in body['scans']):
        raise ValueError('Original scan product is absent from the TEMP plan')
    for row in active:
        totals = expected[row['deliveryCode']]
        totals[0] += 1
        totals[1] += integer(row, 'weightGrams')
    unique(receipt['items'], 'deliveryCode')
    actual = {}
    for row in receipt['items']:
        if set(row) != {'deliveryCode', 'actualCount', 'actualWeightGrams'}:
            raise ValueError('Unsupported TEMP product receipt fields')
        actual[row['deliveryCode']] = [integer(row, 'actualCount'), integer(row, 'actualWeightGrams')]
    same(actual, expected)
    same(receipt['firstOrderScanSeq'], active[0]['orderScanSeq'])
    same(receipt['lastOrderScanSeq'], active[-1]['orderScanSeq'])


def verify_temporary_receipt(payload: bytes, response: bytes, context: dict, trusted_key: dict) -> dict:
    if len(payload) > MAX_PAYLOAD or len(response) > MAX_RESPONSE:
        raise ValueError('Original files exceed resource limits')
    trusted_context(payload, context)
    body, envelope = parse(payload), parse(response)
    same(envelope['code'], 'OK')
    receipt = envelope['data']
    der = verify_signature(receipt, trusted_key)
    original_scope(body, context)
    success_context(body, receipt, context)
    products(body, effective_scans(body, original_captures(body, context)), receipt)
    return {'businessReceiptVerified': True, 'verificationScope': 'HISTORICAL_TEMP_FIRST_ROUND_SUCCESS_RECEIPT',
            'packageIntegrityVerified': False, 'currentBusinessStateVerified': False,
            'releaseAllowed': False, 'localDeletionAllowed': False, 'externalContext': context.copy(),
            'payloadSha256': digest(payload), 'responseSha256': digest(response),
            'receiptKeySha256': digest(der), 'receiptKeyId': trusted_key['keyId'],
            'actualCount': receipt['actualCount'], 'actualWeightGrams': receipt['actualWeightGrams'],
            'notice': '仅核验首轮TEMP历史成功原响应及包外原范围；未核验应急包、现时状态或后续映射，不授放行或删除。'}
