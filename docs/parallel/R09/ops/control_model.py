"""Closed recovery phases and immutable original-command records; no release."""
from control_format import (fields, version, identifier, uuid, revision, checksum,
                            instant, hashed, now, ZERO_HASH, canonical, decode)
from safety import require

COMMAND_FIELDS = ('formatVersion installationId commandId expectedRevision phase epochId '
                  'recoverySessionId snapshotId manifestSha256 affectedWarehouseIds '
                  'possibleLossFrom possibleLossThrough evidenceSha256 actorId requestedAt')
RECORD_FIELDS = ('formatVersion installationId revision previousRecordSha256 command '
                 'commandSha256 createdAt recordSha256')
NEXT_PHASE = {'QUIESCING': 'RECOVERING', 'RECOVERING': 'VERIFYING', 'VERIFYING': 'RELEASE_READY'}
CONTEXT = ('installationId', 'recoverySessionId', 'snapshotId', 'manifestSha256',
           'affectedWarehouseIds', 'possibleLossFrom', 'possibleLossThrough')


def command(value):
    fields(value, COMMAND_FIELDS)
    version(value['formatVersion'])
    for key in ('installationId', 'commandId', 'epochId', 'recoverySessionId'):
        uuid(value[key])
    for key in ('snapshotId', 'actorId'):
        identifier(value[key])
    revision(value['expectedRevision'])
    require(value['phase'] in (*NEXT_PHASE, 'RELEASE_READY'), 'CONTROL_PHASE_CLOSED_ONLY')
    for key in ('manifestSha256', 'evidenceSha256'):
        checksum(value[key])
        require(value[key] != ZERO_HASH, 'CONTROL_EVIDENCE_REQUIRED')
    warehouses(value['affectedWarehouseIds'])
    start, end = (instant(value[key]) for key in ('possibleLossFrom', 'possibleLossThrough'))
    require(start <= end <= instant(value['requestedAt']), 'CONTROL_LOSS_WINDOW')
    return value


def warehouses(value):
    require(type(value) is list and 1 <= len(value) <= 1000, 'CONTROL_WAREHOUSES')
    for item in value:
        identifier(item)
    require(value == sorted(set(value)), 'CONTROL_WAREHOUSE_ORDER')


def transition(previous, cmd):
    expected = '0' if previous is None else previous['revision']
    require(cmd['expectedRevision'] == expected, 'CONTROL_CAS_CONFLICT')
    if previous is None:
        require(cmd['phase'] == 'QUIESCING', 'CONTROL_INITIAL_PHASE')
        return
    before = previous['command']
    require(NEXT_PHASE.get(before['phase']) == cmd['phase'], 'CONTROL_TRANSITION')
    require(all(cmd[key] == before[key] for key in CONTEXT), 'CONTROL_CONTEXT_CHANGED')
    changed = cmd['epochId'] != before['epochId']
    require(changed == (cmd['phase'] == 'RECOVERING'), 'CONTROL_EPOCH_TRANSITION')


def create(previous, cmd):
    command(cmd)
    transition(previous, cmd)
    value = {'formatVersion': 1, 'installationId': cmd['installationId'],
             'revision': str(revision(cmd['expectedRevision']) + 1),
             'previousRecordSha256': previous['recordSha256'] if previous else ZERO_HASH,
             'command': cmd, 'commandSha256': hashed(cmd), 'createdAt': now()}
    return {**value, 'recordSha256': hashed(value)}


def verify(value, previous):
    fields(value, RECORD_FIELDS)
    version(value['formatVersion'])
    cmd = command(value['command'])
    transition(previous, cmd)
    require(value['installationId'] == cmd['installationId'], 'CONTROL_INSTALLATION_MISMATCH')
    require(value['revision'] == str(revision(cmd['expectedRevision']) + 1), 'CONTROL_CHAIN_REVISION')
    expected = previous['recordSha256'] if previous else ZERO_HASH
    require(value['previousRecordSha256'] == expected, 'CONTROL_CHAIN_PREVIOUS')
    require(value['commandSha256'] == hashed(cmd), 'CONTROL_COMMAND_HASH')
    instant(value['createdAt'])
    body = {key: item for key, item in value.items() if key != 'recordSha256'}
    require(value['recordSha256'] == hashed(body), 'CONTROL_RECORD_HASH')
    return value


def checkpoint(record):
    return {'formatVersion': 1, 'installationId': record['installationId'],
            'revision': record['revision'], 'recordSha256': record['recordSha256']}


def view(record):
    record = decode(canonical(record))
    return {'status': 'PASS', 'scope': 'LOCAL_CONTROL_ONLY', 'record': record,
            'checkpoint': checkpoint(record), 'ordinaryWritesAllowed': False,
            'localDeletionAllowed': False, 'sharedGateIntegrated': False}
