"""Reconstruct historical confirmation/approval links without requiring today's roles or sessions."""
from datetime import timedelta, timezone
from archive_proofs import canonical, content, instant
from command_archive_rows import read_record
from command_archive_proofs import command_proof
from temporary_archive_products import decision_products, products
from safety import digest, require


def confirmation(db, files, case):
    proof, body, keys = case['proof'], case['body'], case['keys']
    row = read_record(db, proof['commandReceiptId'])
    receipt = command_proof(row, keys)
    stored = content(files, proof['commandResponseSha256'], proof['commandBytes'])
    require(canonical(stored) == canonical(receipt) and row['responseSha256'] == proof['commandResponseSha256'], 'TEMP_ARCHIVE_COMMAND_ORIGINAL')
    require(len(bytes.fromhex(row['responseHex'])) == int(proof['commandBytes']), 'TEMP_ARCHIVE_COMMAND_SIZE')
    require(receipt['commandType'] == receipt['businessDecision']['kind'] == 'NORMAL_CONFIRM', 'TEMP_ARCHIVE_NORMAL_CONFIRM_REQUIRED')
    context = receipt['originalContext']
    for key in ('submissionId', 'warehouseId', 'authorizationId', 'freezeDeviceId', 'workRound', 'sourceRecoveryEpoch', 'mappingVersion', 'planSha256'):
        require(context[key] == proof[key], 'TEMP_ARCHIVE_COMMAND_SOURCE')
    require(context['canonicalOrderId'] == proof['orderId'] and context['originalOperatorId'] == proof['operatorId'], 'TEMP_ARCHIVE_COMMAND_OWNER')
    require(context['creatorDeviceId'] == proof['source']['creatorDeviceId'] and context['clientOrderId'] == proof['source']['clientOrderId'], 'TEMP_ARCHIVE_COMMAND_SOURCE')
    require(context['receivedPayloadSha256'] == proof['payloadSha256'] and context['declaredUploadSha256'] in (None, proof['payloadSha256']), 'TEMP_ARCHIVE_COMMAND_PAYLOAD')
    decision = receipt['businessDecision']
    require(canonical(decision['submissionReceipt']) == canonical(case['receipt']), 'TEMP_ARCHIVE_COMMAND_SUCCESS')
    candidates(case, decision['candidates'])
    short = decision_products(body, decision)
    if short:
        approval(db, receipt, case)
    else:
        require(decision['shortApprovalReceiptId'] is None and decision['shortApprovalSha256'] is None, 'TEMP_ARCHIVE_UNEXPECTED_APPROVAL')
    return 1 + int(short)


def approval(db, confirmation, case):
    decision = confirmation['businessDecision']
    require(isinstance(decision['shortApprovalReceiptId'], str), 'TEMP_ARCHIVE_APPROVAL_REQUIRED')
    row = read_record(db, decision['shortApprovalReceiptId'])
    receipt = command_proof(row, case['keys'])
    approved = receipt['businessDecision']
    require(receipt['commandType'] == approved['kind'] == 'SHORT_APPROVE' and approved['submissionReceipt'] is None and
            approved['shortApprovalReceiptId'] is None and approved['shortApprovalSha256'] is None, 'TEMP_ARCHIVE_APPROVAL_KIND')
    require(decision_products(case['body'], approved), 'TEMP_ARCHIVE_APPROVAL_NOT_SHORT')
    require(receipt['actor']['principalId'] != receipt['originalContext']['originalOperatorId'], 'TEMP_ARCHIVE_SELF_APPROVAL')
    require(row['responseSha256'] == decision['shortApprovalSha256'], 'TEMP_ARCHIVE_APPROVAL_DIGEST')
    before, after = receipt['originalContext'], confirmation['originalContext']
    require({k: v for k, v in before.items() if k != 'decisionVersion'} ==
            {k: v for k, v in after.items() if k != 'decisionVersion'}, 'TEMP_ARCHIVE_APPROVAL_SOURCE')
    require(receipt['resultingDecisionVersion'] == after['decisionVersion'], 'TEMP_ARCHIVE_APPROVAL_VERSION')
    require(approved['candidates'] == decision['candidates'], 'TEMP_ARCHIVE_APPROVAL_CANDIDATES')
    require(instant(receipt['recordedAt']) <= instant(confirmation['recordedAt']), 'TEMP_ARCHIVE_APPROVAL_TIME')


def candidates(case, value):
    proof, body = case['proof'], case['body']
    require(set(value) == set('policyVersion fromDate throughDate total fingerprint contextSha256'.split()), 'TEMP_ARCHIVE_CANDIDATE_FIELDS')
    day = instant(case['receivedAt']).astimezone(timezone(timedelta(hours=8))).date()
    expected = {'policyVersion': 'REVIEW_DEFAULT_V1', 'fromDate': str(day - timedelta(days=7)),
                'throughDate': str(day + timedelta(days=7)), 'total': '0', 'fingerprint': digest(b'')}
    context = {'products': products(body), 'vehicleNo': body['temporaryOrder']['vehicleNo'],
               'submissionId': proof['submissionId'], 'payloadSha256': proof['payloadSha256']}
    expected['contextSha256'] = digest(canonical({'warehouseId': proof['warehouseId'], 'context': context, 'candidates': dict(expected)}).encode('utf-8'))
    require(canonical(value) == canonical(expected), 'TEMP_ARCHIVE_CANDIDATE_CONTEXT')
