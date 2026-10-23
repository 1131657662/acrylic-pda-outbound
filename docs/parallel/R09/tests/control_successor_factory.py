"""Synthetic NORMAL originals for successor protocol tests; no database authority."""
from uuid import uuid4
from control_factory import fixture, next_command, write
from control_disk import append
from control_format import canonical, hashed, now
from control_store import ControlStore


def release(location, result):
    for phase in ('RECOVERING', 'VERIFYING', 'RELEASE_READY'):
        result = ControlStore(location).apply(next_command(result, phase), result['checkpoint'])
    return result


def released(root=None):
    base, location, initial = fixture(root) if root else fixture()
    result = release(location, ControlStore(location).initialize(initial))
    return base, location, initial, result


def normal(location, result):
    record, cmd = result['record'], result['record']['command']
    body = {'formatVersion': 1, 'installationId': location.installation_id,
            'expectedDatabaseSchema': cmd.get('targetDatabaseSchema', 'synthetic_source'),
            'expectedBaselineNumericEpoch': 1, 'controlRevision': int(record['revision']),
            'controlRecordSha256': record['recordSha256'], 'previousRecordSha256': record['previousRecordSha256'],
            'operatorId': 'synthetic-operator', 'requestedAt': now()}
    value = {**body, 'intentSha256': hashed(body)}
    target = location.root / 'normal-admission.json'
    if target.exists():
        directory = location.root / 'normal-history'
        directory.mkdir(exist_ok=True)
        target = directory / (value['intentSha256'] + '.json')
    append(target, canonical(value))
    return value, target


def successor(result, intent):
    return {**next_command(result, 'QUIESCING'), 'formatVersion': 2,
            'recoverySessionId': str(uuid4()), 'snapshotId': 'successor-snapshot',
            'predecessorReleaseSha256': result['record']['recordSha256'],
            'predecessorNormalIntentSha256': intent['intentSha256'],
            'sourceBindingSha256': 'c' * 64, 'sourceNormalSha256': 'd' * 64,
            'targetServerUuid': str(uuid4()), 'targetDatabaseSchema': 'synthetic_target'}


def append_unchecked(location, result, cmd, pending=False):
    """Rehashed hostile producer exercises reader validation beyond digest checks."""
    previous = result['record']
    revision = int(previous['revision']) + 1
    body = {'formatVersion': 1, 'installationId': location.installation_id,
            'targetRevision': str(revision), 'previousRecordSha256': previous['recordSha256'],
            'command': cmd, 'commandSha256': hashed(cmd)}
    intent = {**body, 'intentSha256': hashed(body)}
    append(location.root / 'intents' / f'{revision:018d}.json', canonical(intent))
    write(location.root / 'admission.json', {k: intent[k] for k in (
        'formatVersion', 'installationId', 'targetRevision', 'intentSha256')})
    if pending:
        return
    body = {k: v for k, v in body.items() if k != 'targetRevision'}
    body.update(revision=str(revision), createdAt=now())
    record = {**body, 'recordSha256': hashed(body)}
    append(location.root / 'history' / f'{revision:018d}.json', canonical(record))
    write(location.root / 'current.json', {'formatVersion': 1, 'installationId': location.installation_id,
                                         'revision': str(revision), 'recordSha256': record['recordSha256']})
