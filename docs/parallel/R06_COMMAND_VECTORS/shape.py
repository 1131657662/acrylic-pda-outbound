"""Check C0 proposal field shapes, without authorizing any business action."""
import re
import uuid

BODY = {'commandVersion', 'commandType', 'commandId', 'warehouseId', 'source',
        'submissionId', 'workRound', 'authorizationId', 'freezeDeviceId',
        'sourceRecoveryEpoch', 'transportRecoveryEpoch', 'expected', 'reason'}
EXPECTED = {'canonicalOrderId', 'mappingVersion', 'currentSubmissionId', 'scopeRound',
            'slotVersion', 'decisionVersion', 'receivedPayloadSha256', 'declaredUploadSha256',
            'planSha256', 'targetRevision', 'candidateContextSha256', 'userAuthVersion'}
KINDS = {'DUPLICATE_EXCLUDE', 'LINK', 'REJECT', 'SHORT_APPROVE', 'NORMAL_CONFIRM',
         'PHYSICAL_DISPOSITION', 'REOPEN_WORK'}
REASON_REQUIRED = KINDS - {'NORMAL_CONFIRM'}


def require(valid, code):
    if not valid:
        raise ValueError(code)


def fields(value, expected):
    require(type(value) is dict and set(value) == expected, 'FIELDS_INVALID')


def identifier(value):
    require(isinstance(value, str), 'UUID_INVALID')
    try:
        require(str(uuid.UUID(value)) == value, 'UUID_INVALID')
    except (ValueError, AttributeError) as error:
        raise ValueError('UUID_INVALID') from error


def decimal(value):
    require(isinstance(value, str) and re.fullmatch(r'0|[1-9][0-9]{0,17}', value), 'DECIMAL_INVALID')


def hash_value(value):
    require(isinstance(value, str) and re.fullmatch(r'[a-f0-9]{64}', value), 'HASH_INVALID')


def source(value):
    fields(value, {'kind', 'creatorDeviceId', 'clientOrderId'})
    require(value['kind'] == 'TEMP', 'SOURCE_KIND_INVALID')
    identifier(value['creatorDeviceId'])
    identifier(value['clientOrderId'])


def expected(value):
    fields(value, EXPECTED)
    identifier(value['currentSubmissionId'])
    for key in ('mappingVersion', 'scopeRound', 'decisionVersion', 'userAuthVersion'):
        decimal(value[key])
    for key in ('receivedPayloadSha256', 'declaredUploadSha256', 'planSha256', 'candidateContextSha256'):
        if value[key] is not None:
            hash_value(value[key])
    if value['canonicalOrderId'] is None:
        require(value['mappingVersion'] == '0' and value['slotVersion'] is None
                and value['targetRevision'] is None, 'NULL_MAPPING_INVALID')
        return
    identifier(value['canonicalOrderId'])
    require(value['mappingVersion'] != '0', 'MAPPING_VERSION_INVALID')
    decimal(value['slotVersion'])
    decimal(value['targetRevision'])


def command(value):
    fields(value, BODY)
    require(type(value['commandVersion']) is int and value['commandVersion'] == 1, 'VERSION_INVALID')
    require(value['commandType'] in KINDS, 'COMMAND_KIND_INVALID')
    for key in ('commandId', 'warehouseId', 'submissionId', 'authorizationId', 'freezeDeviceId'):
        identifier(value[key])
    for key in ('workRound', 'sourceRecoveryEpoch', 'transportRecoveryEpoch'):
        decimal(value[key])
    require(value['workRound'] != '0', 'ROUND_INVALID')
    require(isinstance(value['reason'], str), 'REASON_TYPE_INVALID')
    if value['commandType'] in REASON_REQUIRED:
        require(bool(value['reason'].strip()), 'REASON_REQUIRED')
    source(value['source'])
    expected(value['expected'])
