"""Decode actual all-column snapshots to verify successor source pins without JDBC."""
import base64
from datetime import datetime
import uuid

from verify_d13_restored_closed_p5 import hashed


def rows(snapshot, table):
    fact = snapshot['tables'][table]
    return [dict(zip(fact['columns'], [None if cell is None else base64.b64decode(cell)
                                      for cell in row], strict=True)) for row in fact['rows']]


def original_hash(snapshot, table):
    values = rows(snapshot, table)
    assert len(values) == 1
    result = dict(formatVersion=1, table=table)
    for name, raw in values[0].items():
        assert raw is not None
        if name in ('installation_id', 'mysql_server_uuid'):
            value = str(uuid.UUID(bytes=raw))
        elif name.endswith('_sha256'):
            value = raw.hex()
        elif name == 'registered_at':
            value = datetime.fromisoformat(raw.decode()).strftime('%Y-%m-%dT%H:%M:%S.%f')
        elif name in ('id', 'format_version', 'baseline_numeric_epoch', 'authority_revision',
                      'control_revision', 'numeric_epoch'):
            value = int(raw)
        else:
            value = raw.decode()
        result[name] = value
    return hashed(result)


def binding(after, records, pins):
    values = rows(after, 'recovery_successor_binding')
    assert len(values) == 1
    row = values[0]
    value = dict(formatVersion=1, installationId=str(uuid.UUID(bytes=row['installation_id'])),
        startRevision=int(row['start_control_revision']), startHash=row['start_control_sha256'].hex(),
        predecessorRelease=row['predecessor_release_sha256'].hex(),
        predecessorIntent=row['predecessor_intent_sha256'].hex(), sourceBinding=row['source_binding_sha256'].hex(),
        sourceNormal=row['source_normal_sha256'].hex(), serverUuid=str(uuid.UUID(bytes=row['mysql_server_uuid'])),
        schema=row['database_schema'].decode())
    assert hashed(value) == row['binding_sha256'].hex()
    assert value['startRevision'] == 5 and value['startHash'] == records[4]['recordSha256']
    assert value['sourceBinding'] == pins['sourceBindingSha256']
    assert value['sourceNormal'] == pins['sourceNormalSha256']
    assert value['predecessorRelease'] == pins['predecessorReleaseSha256'] == records[3]['recordSha256']
    assert value['predecessorIntent'] == pins['predecessorNormalIntentSha256']
    assert value['serverUuid'] == records[4]['command']['targetServerUuid']
    assert value['schema'] == records[4]['command']['targetDatabaseSchema']
    return value


def normal(after, records, intent):
    values = rows(after, 'recovery_successor_normal')
    assert len(values) == 1
    row = values[0]
    value = dict(formatVersion=1, installationId=str(uuid.UUID(bytes=row['installation_id'])),
        bindingRevision=int(row['binding_revision']), revision=int(row['control_revision']),
        recordHash=row['control_record_sha256'].hex(), previousHash=row['previous_record_sha256'].hex(),
        intentHash=row['intent_sha256'].hex(), numeric=int(row['numeric_epoch']), operator=row['operator_id'].decode())
    assert hashed(value) == row['normal_sha256'].hex()
    assert value['bindingRevision'] == 5 and value['revision'] == 8
    assert value['recordHash'] == records[-1]['recordSha256'] == intent['controlRecordSha256']
    assert value['previousHash'] == intent['previousRecordSha256']
    assert value['intentHash'] == intent['intentSha256'] and value['operator'] == intent['operatorId']
    state = rows(after, 'server_state')
    assert len(state) == 1 and state[0]['service_mode'] == b'NORMAL'
    assert int(state[0]['recovery_epoch']) == value['numeric']
    return value
