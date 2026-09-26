"""Strict signature and immutable database closure for archived real TEMP decisions."""
import base64
import re
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from archive_proofs import canonical, parse, instant
from safety import digest, require, OpsError

CAPABILITIES = dict.fromkeys(('writeConfirmationAvailable', 'releaseAllowed', 'mayResumeScanning', 'stockReleased', 'localDeletionAllowed'), False)


def decode(segment):
    require(isinstance(segment, str) and re.fullmatch('[A-Za-z0-9_-]+', segment), 'COMMAND_TOKEN_ENCODING')
    value = base64.urlsafe_b64decode(segment + '=' * (-len(segment) % 4))
    require(base64.urlsafe_b64encode(value).rstrip(b'=').decode('ascii') == segment, 'COMMAND_TOKEN_ENCODING')
    return value


def signed(value, keys, audience):
    public, kid = keys
    token = value['signedReceipt']
    require(isinstance(token, str) and len(token) <= 262144, 'COMMAND_TOKEN_SIZE')
    parts = token.split('.')
    require(len(parts) == 3, 'COMMAND_TOKEN_SHAPE')
    header, claims = parse(decode(parts[0])), parse(decode(parts[1]))
    require(header == {'alg': 'RS256', 'typ': 'JWT', 'kid': kid}, 'COMMAND_SIGNATURE_KEY')
    require(set(claims) == {'iss', 'aud', 'receipt'} and claims['iss'] == 'acrylic-outbound' and claims['aud'] == audience, 'COMMAND_SIGNATURE_PURPOSE')
    try:
        public.verify(decode(parts[2]), '.'.join(parts[:2]).encode('ascii'), padding.PKCS1v15(), hashes.SHA256())
    except InvalidSignature:
        raise OpsError('COMMAND_SIGNATURE_INVALID') from None
    unsigned = {key: item for key, item in value.items() if key != 'signedReceipt'}
    require(canonical(unsigned) == canonical(claims['receipt']), 'COMMAND_SIGNED_CONTENT')
    return unsigned


def command_proof(row, keys):
    command_bytes, raw = bytes.fromhex(row['commandHex']), bytes.fromhex(row['responseHex'])
    require(0 < len(command_bytes) <= 262144 and 0 < len(raw) <= 262144, 'COMMAND_SIZE')
    command, receipt = parse(command_bytes), parse(raw)
    require(command_bytes == canonical(command).encode('utf-8'), 'COMMAND_ORIGINAL_BYTES')
    require(digest(b'ACRYLIC-R06/COMMAND/1\n' + command_bytes) == row['commandSha256'] == row['requestSha256'], 'COMMAND_DIGEST')
    require(digest(raw) == row['responseSha256'] and receipt['signedReceipt'] == row['token'], 'COMMAND_RESPONSE_DIGEST')
    unsigned = signed(receipt, keys, 'acrylic-r06-command-receipt-v1')
    encoded = canonical(unsigned).encode('utf-8')
    require(digest(b'ACRYLIC-R06/RECEIPT/1\n' + encoded) == row['unsignedSha256'], 'COMMAND_RECEIPT_DIGEST')
    require(bytes.fromhex(row['eventHex']) == encoded, 'COMMAND_EVENT_BYTES')
    require(row['keyId'] == keys[1] and row['resourceId'] == row['id'], 'COMMAND_DATABASE_LINK')
    header_closure(row, command, unsigned)
    context_closure(row, command, unsigned)
    expected_closure(command, unsigned)
    return receipt


def header_closure(row, command, receipt):
    require(set(receipt) == set(('receiptVersion receiptId commandId routeScope commandType commandSha256 status businessDecision '
                                'recordedAt eventId resultingDecisionVersion originalContext actor expected capabilities').split()), 'COMMAND_RECEIPT_FIELDS')
    require(type(receipt['receiptVersion']) is int and receipt['receiptVersion'] == 1, 'COMMAND_RECEIPT_VERSION')
    for key, expected in {'receiptId': row['id'], 'commandId': row['commandId'], 'commandType': row['commandType'],
                          'commandSha256': row['commandSha256'], 'routeScope': 'r06.command.v1', 'status': 'RECORDED',
                          'eventId': row['eventId'], 'resultingDecisionVersion': row['eventVersion'], 'capabilities': CAPABILITIES}.items():
        require(canonical(receipt[key]) == canonical(expected), 'COMMAND_HEADER_MISMATCH')
    require(command['commandId'] == row['commandId'] and command['commandType'] == row['commandType'] and
            row['routeScope'] == receipt['routeScope'], 'COMMAND_REQUEST_MISMATCH')
    require(instant(receipt['recordedAt']) == instant(row['recordedAt']) == instant(row['eventAt']), 'COMMAND_TIME_MISMATCH')
    require(row['eventScopeId'] == row['scopeId'] and row['eventKind'] == receipt['businessDecision']['kind'], 'COMMAND_EVENT_MISMATCH')
    audit = row['audit']; details = audit['details']
    if isinstance(details, str):
        details = parse(details)
    require(audit['actorId'] == row['actorId'] and audit['eventType'] == 'TEMPORARY_' + row['commandType'] and
            details['commandSha256'] == row['commandSha256'] and details['originalResponseSha256'] == row['responseSha256'], 'COMMAND_AUDIT_MISMATCH')


def context_closure(row, command, receipt):
    context, actor = receipt['originalContext'], receipt['actor']
    names = ('scopeId warehouseId submissionId creatorDeviceId clientOrderId authorizationId originalOperatorId '
             'sourceRecoveryEpoch workRound canonicalOrderId mappingId mappingOrderId mappingVersion').split()
    require(all(context[key] == row[key] for key in names), 'COMMAND_SOURCE_MISMATCH')
    require(row['mappingScopeId'] == row['scopeId'] and row['mappingWarehouseId'] == row['warehouseId'] and
            row['mappingOrderId'] == row['canonicalOrderId'], 'COMMAND_MAPPING_MISMATCH')
    for key in ('warehouseId', 'submissionId', 'authorizationId', 'freezeDeviceId', 'workRound', 'sourceRecoveryEpoch'):
        require(command[key] == context[key], 'COMMAND_FROZEN_MISMATCH')
    require(command['source'] == {'kind': 'TEMP', 'creatorDeviceId': context['creatorDeviceId'], 'clientOrderId': context['clientOrderId']}, 'COMMAND_SOURCE_SHAPE')
    require(receipt['expected'] == command['expected'] and actor['principalId'] == row['actorId'] and
            actor['userAuthVersion'] == command['expected']['userAuthVersion'], 'COMMAND_ACTOR_MISMATCH')
    permission = 'submission:review' if row['commandType'] == 'SHORT_APPROVE' else 'temporary_order:confirm'
    snapshot = actor['permissionSnapshot']
    require(snapshot['requiredPermissions'] == [permission] and permission in snapshot['effectivePermissions'] and
            row['warehouseId'] in snapshot['warehouseIds'], 'COMMAND_HISTORICAL_PERMISSION')
    require(receipt['resultingDecisionVersion'] == str(int(context['decisionVersion']) + 1), 'COMMAND_DECISION_VERSION')


def expected_closure(command, receipt):
    context = receipt['originalContext']
    require(type(command['commandVersion']) is int and command['commandVersion'] == 1, 'COMMAND_VERSION')
    names = ('canonicalOrderId mappingVersion currentSubmissionId scopeRound slotVersion decisionVersion '
             'receivedPayloadSha256 declaredUploadSha256 planSha256 targetRevision').split()
    expected = {key: context[key] for key in names}
    absent = ('evidenceSetId evidenceSetSha256 verificationId verificationVersion verificationAlgorithmVersion '
              'dispositionManifestSha256 dispositionManifestVersion retainedManifestSha256 retainedManifestVersion').split()
    expected.update(dict.fromkeys(absent))
    expected.update(userAuthVersion=receipt['actor']['userAuthVersion'], candidateContextSha256=receipt['businessDecision']['candidates']['contextSha256'])
    require(canonical(command['expected']) == canonical(expected), 'COMMAND_EXPECTED_MISMATCH')
