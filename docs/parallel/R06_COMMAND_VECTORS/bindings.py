"""Offline binding and immutable-replay rules; no store, lock, or policy engine."""
from canonical import digest, raw_digest
from shape import command, fields, require

CONTEXT_FIELDS = {
    'warehouseId': 'WAREHOUSE_CHANGED', 'source': 'SOURCE_CHANGED',
    'submissionId': 'SUBMISSION_CHANGED', 'workRound': 'ROUND_CHANGED',
    'authorizationId': 'AUTHORIZATION_CHANGED', 'freezeDeviceId': 'FREEZE_DEVICE_CHANGED',
    'sourceRecoveryEpoch': 'SOURCE_EPOCH_CHANGED',
    'transportRecoveryEpoch': 'TRANSPORT_EPOCH_CHANGED', 'expected': 'EXPECTED_CONTEXT_CHANGED',
}


def check_new_binding(body, saved, identity):
    command(body)
    require(body['commandId'] == identity['originalKey'], 'KEY_BINDING_INVALID')
    require(identity['currentWriteAllowed'] is True, 'WRITE_AUTHORIZATION_REQUIRED')
    for field, code in CONTEXT_FIELDS.items():
        require(body[field] == saved[field], code)
    return 'CONTEXT_MATCH_ONLY'


def original_replay(request, stored, identity):
    require(identity['currentReadAllowed'] is True, 'READ_AUTHORIZATION_REQUIRED')
    for field in ('principalId', 'routeScope', 'originalKey'):
        require(identity[field] == stored['identity'][field], 'ORIGINAL_COMMAND_NOT_FOUND')
    command(request)
    require(request['commandId'] == identity['originalKey'], 'KEY_BINDING_INVALID')
    require(digest(request, 'command') == stored['commandSha256'], 'IDEMPOTENCY_CONFLICT')
    require(raw_digest(stored['rawResponse']) == stored['originalResponseSha256'], 'ORIGINAL_RESPONSE_DAMAGED')
    return stored['rawResponse']


def check_receipt_binding(receipt, saved, identity):
    fields(receipt, {'receiptVersion', 'commandId', 'originalKey', 'routeScope', 'commandType',
                    'commandSha256', 'originalContext', 'actor', 'originalOperatorId', 'decision',
                    'committedAt', 'writeAvailable', 'releaseAllowed', 'stockReleased',
                    'mayResumeScanning', 'localDeletionAllowed'})
    require(type(receipt['receiptVersion']) is int and receipt['receiptVersion'] == 1, 'RECEIPT_VERSION_INVALID')
    require(receipt['commandId'] == saved['commandId'], 'RECEIPT_KEY_CHANGED')
    require(receipt['originalKey'] == identity['originalKey'], 'RECEIPT_KEY_CHANGED')
    require(receipt['routeScope'] == identity['routeScope'], 'RECEIPT_SCOPE_CHANGED')
    require(receipt['commandType'] == saved['commandType'], 'RECEIPT_KIND_CHANGED')
    require(receipt['commandSha256'] == digest(saved, 'command'), 'COMMAND_HASH_CHANGED')
    require(receipt['actor']['principalId'] == identity['principalId'], 'RECEIPT_ACTOR_CHANGED')
    require(receipt['actor']['userAuthVersion'] == saved['expected']['userAuthVersion'], 'RECEIPT_AUTH_CHANGED')
    context = receipt['originalContext']
    fields(context, set(CONTEXT_FIELDS))
    for field, code in CONTEXT_FIELDS.items():
        require(context[field] == saved[field], code)
    for flag in ('writeAvailable', 'releaseAllowed', 'stockReleased', 'mayResumeScanning', 'localDeletionAllowed'):
        require(receipt[flag] is False, 'UNSUPPORTED_CAPABILITY')
    return receipt
