"""Append-only successor rounds; source DB digests are pins, never DB admission."""
import re
from control_disk import read
from control_format import fields, version, uuid, checksum, hashed, instant, ZERO_HASH
from control_paths import checked
from safety import require

EXTRA_FIELDS = ('predecessorReleaseSha256 predecessorNormalIntentSha256 sourceBindingSha256 '
                'sourceNormalSha256 targetServerUuid targetDatabaseSchema')
NORMAL_FIELDS = ('formatVersion installationId expectedDatabaseSchema expectedBaselineNumericEpoch '
                 'controlRevision controlRecordSha256 previousRecordSha256 operatorId requestedAt intentSha256')


def validate_fields(value):
    for key in EXTRA_FIELDS.split()[:4]:
        checksum(value[key])
        require(value[key] != ZERO_HASH, 'CONTROL_SUCCESSOR_EVIDENCE_REQUIRED')
    uuid(value['targetServerUuid'])
    require(type(value['targetDatabaseSchema']) is str and bool(re.fullmatch(
        r'[A-Za-z0-9_]{1,64}', value['targetDatabaseSchema'])), 'CONTROL_SUCCESSOR_SCHEMA')


def round_transition(previous, cmd):
    before = previous['command']
    if before['phase'] == 'RELEASE_READY':
        require(cmd['formatVersion'] == 2 and cmd['phase'] == 'QUIESCING', 'CONTROL_SUCCESSOR_TRANSITION')
        require(cmd['installationId'] == before['installationId'], 'CONTROL_INSTALLATION_MISMATCH')
        require(cmd['recoverySessionId'] != before['recoverySessionId'], 'CONTROL_SESSION_REUSED')
        require(cmd['epochId'] == before['epochId'], 'CONTROL_EPOCH_TRANSITION')
        require(cmd['predecessorReleaseSha256'] == previous['recordSha256'], 'CONTROL_SUCCESSOR_PREDECESSOR')
        return True
    require(cmd['formatVersion'] == before['formatVersion'], 'CONTROL_ROUND_VERSION')
    if cmd['formatVersion'] == 2:
        require(all(cmd[key] == before[key] for key in EXTRA_FIELDS.split()), 'CONTROL_SUCCESSOR_CONTEXT_CHANGED')
    return False


class Rounds:
    def __init__(self):
        self.sessions, self.epochs = set(), set()

    def accept(self, cmd):
        if cmd['phase'] == 'QUIESCING':
            require(cmd['recoverySessionId'] not in self.sessions, 'CONTROL_SESSION_REUSED')
            self.sessions.add(cmd['recoverySessionId'])
        if not self.epochs or cmd['phase'] == 'RECOVERING':
            require(cmd['epochId'] not in self.epochs, 'CONTROL_EPOCH_REUSED')
            self.epochs.add(cmd['epochId'])


def normal(value, records):
    fields(value, NORMAL_FIELDS)
    version(value['formatVersion'])
    uuid(value['installationId'])
    for key in ('expectedBaselineNumericEpoch', 'controlRevision'):
        require(type(value[key]) is int and 0 < value[key] <= 9223372036854775807, 'CONTROL_SUCCESSOR_NORMAL_NUMBER')
    for key in ('expectedDatabaseSchema', 'operatorId'):
        require(type(value[key]) is str and value[key].strip(), 'CONTROL_SUCCESSOR_NORMAL_TEXT')
    for key in ('controlRecordSha256', 'previousRecordSha256', 'intentSha256'):
        checksum(value[key])
    instant(value['requestedAt'])
    require(value['intentSha256'] == hashed({k: v for k, v in value.items() if k != 'intentSha256'}),
            'CONTROL_NORMAL_INTENT_HASH')
    require(value['controlRevision'] <= len(records), 'CONTROL_SUCCESSOR_NORMAL_BINDING')
    record = records[value['controlRevision'] - 1]
    require(record['command']['phase'] == 'RELEASE_READY' and all(value[k] == record[r] for k, r in (
        ('installationId', 'installationId'), ('controlRecordSha256', 'recordSha256'),
        ('previousRecordSha256', 'previousRecordSha256'))), 'CONTROL_SUCCESSOR_NORMAL_BINDING')
    if record['command']['formatVersion'] == 2:
        require(value['expectedDatabaseSchema'] == record['command']['targetDatabaseSchema'],
                'CONTROL_SUCCESSOR_NORMAL_BINDING')
    return value


def originals(root, records, include_initial=False):
    values, paths = {}, []
    initial = root / 'normal-admission.json'
    if include_initial and (initial.exists() or initial.is_symlink()):
        paths.append(initial)
    directory = root / 'normal-history'
    if directory.exists() or directory.is_symlink():
        require(checked(directory).is_dir(), 'CONTROL_SUCCESSOR_NORMAL_LAYOUT')
        entries = list(directory.iterdir())
        require(len(entries) <= 100000, 'CONTROL_SUCCESSOR_NORMAL_LIMIT')
        paths.extend(entries)
    for path in paths:
        value = normal(read(path), records)
        digest = value['intentSha256']
        require(path == initial or path.name == digest + '.json', 'CONTROL_SUCCESSOR_NORMAL_LAYOUT')
        require(digest not in values, 'CONTROL_SUCCESSOR_NORMAL_DUPLICATE')
        values[digest] = value
    return values


def reference(cmd, values):
    if cmd['formatVersion'] != 2 or cmd['phase'] != 'QUIESCING':
        return
    value = values.get(cmd['predecessorNormalIntentSha256'])
    require(value is not None, 'CONTROL_SUCCESSOR_NORMAL_REQUIRED')
    require(value['controlRecordSha256'] == cmd['predecessorReleaseSha256'], 'CONTROL_SUCCESSOR_NORMAL_BINDING')


def verify_history(root, records):
    values = originals(root, records, any(row['command']['formatVersion'] == 2 for row in records))
    rounds = Rounds()
    for record in records:
        rounds.accept(record['command'])
        reference(record['command'], values)
    return values, rounds


def check_pending(root, records, cmd):
    require(all(row['command']['commandId'] != cmd['commandId'] for row in records), 'CONTROL_COMMAND_REUSED')
    values, rounds = verify_history(root, records)
    rounds.accept(cmd)
    if cmd['formatVersion'] == 2:
        values = originals(root, records, include_initial=True)
    reference(cmd, values)


def before_append(root, records, cmd):
    match = next((row for row in records if row['command']['commandId'] == cmd['commandId']), None)
    if match:
        require(match['commandSha256'] == hashed(cmd), 'CONTROL_COMMAND_CONFLICT')
        return
    from control_model import transition
    transition(records[-1], cmd)
    check_pending(root, records, cmd)
