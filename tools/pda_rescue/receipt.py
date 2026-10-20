"""Read-only FORMAL historical success verification; never current release authority."""
from datetime import datetime
import hashlib

from .facts import SCAN_FIELDS, integer, ordered, unique
from .integrity import parse
from .receipt_signature import same, verify_signature
from .verify import verify

FIELDS = {'submissionId', 'orderId', 'status', 'payloadSha256', 'freezeDeviceId', 'workRound',
          'issuedRecoveryEpoch', 'mayResumeScanning', 'stockReleased', 'source', 'orderStatus',
          'actualCount', 'actualWeightGrams', 'inventoryDeducted', 'resultVersion', 'receiptVersion',
          'captureHighWatermark', 'firstOrderScanSeq', 'lastOrderScanSeq', 'completedAt', 'orderNo',
          'items', 'signedReceipt'}
FROZEN_SCAN_FIELDS = SCAN_FIELDS + ('baleId', 'expectedDispatchCycle', 'localSeq')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def original_context(result):
    files, facts = result['files'], result['facts']
    if not result['sourceTrusted'] or facts['order']['source'] != 'FORMAL':
        raise ValueError('Trusted original FORMAL registration is required')
    if not {'submission.json', 'submission-info.json', 'receipt.json'} <= files.keys():
        raise ValueError('Original frozen payload and receipt are required')
    body, receipt = parse(files['submission.json']), parse(files['receipt.json'])
    if not isinstance(body, dict) or not isinstance(receipt, dict):
        raise ValueError('Frozen payload and receipt must be objects')
    source = {'kind': 'FORMAL', 'sourceOrderId': facts['order']['orderId']}
    same(body['source'], source)
    same(body['freezeDeviceId'], facts['order']['deviceId'])
    same(body['assignmentId'], facts['order']['localOrderId'])
    return body, receipt


def context(body, receipt, payload):
    optional = {'localDeletionAllowed'} if 'localDeletionAllowed' in receipt else set()
    if set(receipt) != FIELDS | optional:
        raise ValueError('Unsupported FORMAL success receipt fields')
    if optional:
        same(receipt['localDeletionAllowed'], False)
    expected = {'submissionId': body['submissionId'], 'orderId': body['source']['sourceOrderId'],
                'status': 'SUCCEEDED', 'orderStatus': 'COMPLETED', 'resultVersion': '1', 'receiptVersion': 1,
                'payloadSha256': digest(payload), 'freezeDeviceId': body['freezeDeviceId'],
                'workRound': body['workRound'], 'issuedRecoveryEpoch': body['sourceRecoveryEpoch'],
                'captureHighWatermark': body['captureHighWatermark'], 'source': body['source'],
                'actualCount': body['expectedCount'], 'actualWeightGrams': body['expectedWeightGrams'],
                'inventoryDeducted': body['autoDeductInventory'], 'mayResumeScanning': False, 'stockReleased': False}
    same({name: receipt[name] for name in expected}, expected)
    if type(body['autoDeductInventory']) is not bool:
        raise ValueError('Inventory mode must be boolean')
    for field in ['workRound', 'sourceRecoveryEpoch', 'captureHighWatermark', 'expectedCount', 'expectedWeightGrams']:
        integer(body, field)
    if integer(body, 'workRound') < 1 or integer(body, 'expectedCount') < 1:
        raise ValueError('Success must contain a positive round and effective scans')
    if not isinstance(receipt['orderNo'], str) or not receipt['orderNo'].strip():
        raise ValueError('Missing historical order number')
    if not isinstance(receipt['completedAt'], str) or datetime.fromisoformat(receipt['completedAt']).tzinfo is None:
        raise ValueError('Missing historical completion time zone')


def original_scans(body, facts):
    scans = body['scans']
    unique(scans, 'captureId'); ordered(scans, 'orderScanSeq')
    same([{field: row[field] for field in FROZEN_SCAN_FIELDS} for row in scans],
         [{field: row[field] for field in FROZEN_SCAN_FIELDS} for row in facts['scans']])
    proofs = body.get('dispositions', [])
    unique(proofs, 'captureId')
    same([{'captureId': row['captureId'], 'evidenceId': row['approvedEvidenceId'], 'action': row['action']} for row in proofs],
         [{field: row[field] for field in ['captureId', 'evidenceId', 'action']} for row in facts['dispositions']])
    for proof, row in zip(proofs, facts['dispositions']):
        same(proof, parse(row['proofJson'].encode('utf-8')))
    excluded = {row['captureId'] for row in proofs}
    active = [row for row in scans if row['status'] == 'ACTIVE' and row['captureId'] not in excluded]
    if not active or str(len(active)) != body['expectedCount']:
        raise ValueError('Effective frozen count differs from signed result')
    if str(sum(integer(row, 'weightGrams') for row in active)) != body['expectedWeightGrams']:
        raise ValueError('Effective frozen integer grams differ from signed result')
    return active


def original_captures(body, facts):
    fields = ('id', 'orderScanSeq', 'operatorId', 'warehouseId', 'originDeviceId', 'assignmentId')
    unique(body['captures'], 'id'); ordered(body['captures'], 'orderScanSeq')
    same([{field: row[field] for field in fields} for row in body['captures']],
         [{field: row[field] for field in fields} for row in facts['captures']])
    if not body['captures']:
        raise ValueError('Frozen success has no original capture scope')
    for row in body['captures']:
        same(row['operatorId'], facts['order']['operatorId'])
        same(row['warehouseId'], facts['order']['warehouseId'])


def products(active, receipt):
    expected = {}
    for row in active:
        count, weight = expected.get(row['deliveryCode'], (0, 0))
        expected[row['deliveryCode']] = (count + 1, weight + integer(row, 'weightGrams'))
    unique(receipt['items'], 'deliveryCode')
    actual = {row['deliveryCode']: (integer(row, 'actualCount'), integer(row, 'actualWeightGrams'))
              for row in receipt['items']}
    if {code: totals for code, totals in actual.items() if totals != (0, 0)} != expected:
        raise ValueError('Signed per-product totals differ from effective frozen records')
    same(receipt['firstOrderScanSeq'], active[0]['orderScanSeq'])
    same(receipt['lastOrderScanSeq'], active[-1]['orderScanSeq'])


def verify_formal_receipt(plain: bytes, registration: dict | None, trusted_key: dict) -> dict:
    result = verify(plain, registration)
    body, receipt = original_context(result)
    der = verify_signature(receipt, trusted_key)
    context(body, receipt, result['files']['submission.json'])
    original_captures(body, result['facts'])
    products(original_scans(body, result['facts']), receipt)
    return {'integrityVerified': True, 'sourceTrusted': True, 'businessReceiptVerified': True,
            'verificationScope': 'HISTORICAL_FORMAL_SUCCESS_RECEIPT', 'currentBusinessStateVerified': False,
            'releaseAllowed': False, 'localDeletionAllowed': False, 'submissionId': body['submissionId'],
            'payloadSha256': digest(result['files']['submission.json']),
            'receiptSha256': digest(result['files']['receipt.json']), 'receiptKeySha256': digest(der),
            'archiveSha256': digest(plain), 'receiptKeyId': trusted_key['keyId'],
            'manifestSha256': digest(result['files']['manifest.json']), 'summary': result['manifest']['summary'],
            'notice': '仅核验封存的FORMAL成功原回执；不查询后续更正/撤回或现时业务状态，不授作业、放行或删除权限。'}
