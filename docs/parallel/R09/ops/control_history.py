"""Read the entire immutable chain, exact pointer and caller's trusted anchor."""
from pathlib import Path
import re
from control_disk import read
from control_paths import checked
from control_format import fields, version, uuid, revision, checksum
from control_model import verify, checkpoint
from control_identity import validate as validate_identity
from safety import require


def history_name(number):
    return f'{number:018d}.json'


def history(root):
    directory = checked(root / 'history')
    require(directory.is_dir(), 'CONTROL_HISTORY_MISSING')
    paths = sorted(directory.iterdir())
    require(len(paths) <= 100000, 'CONTROL_HISTORY_LIMIT')
    records, ids = [], set()
    for number, path in enumerate(paths, 1):
        require(path.name == history_name(number), 'CONTROL_HISTORY_GAP')
        value = verify(read(path), records[-1] if records else None)
        require(value['command']['commandId'] not in ids, 'CONTROL_COMMAND_REUSED')
        ids.add(value['command']['commandId'])
        records.append(value)
    return records


def anchor(value, records):
    fields(value, 'formatVersion installationId revision recordSha256')
    version(value['formatVersion'])
    uuid(value['installationId'])
    checksum(value['recordSha256'])
    number = revision(value['revision'])
    require(1 <= number <= len(records), 'CONTROL_CHECKPOINT_ROLLBACK')
    require(value == checkpoint(records[number - 1]), 'CONTROL_CHECKPOINT_MISMATCH')
    return number


def current(root, records):
    path = root / 'current.json'
    if not path.exists():
        require(len(records) <= 1, 'CONTROL_POINTER_MISSING')
        return 0
    number = anchor(read(path), records)
    require(len(records) - number <= 1, 'CONTROL_POINTER_ROLLBACK')
    return number


def layout(root, records, number):
    allowed = {'identity.json', 'coordinator.lock', 'history', 'current.json'}
    from control_writers import validate
    if validate(root, read(root / 'identity.json')):
        allowed.update(('writers.json', 'writers.lock'))
        if read(root / 'writers.json')['formatVersion'] == 2:
            from control_intents import inspect
            _, marker, latest, _ = inspect(root, records, complete=False)
            allowed.update(('intents', 'admission.json'))
            if marker != latest:
                allowed.add('admission-' + latest['intentSha256'] + '.pending')
    if (root / 'closed-sessions').exists():
        closed_sessions(root / 'closed-sessions')
        allowed.add('closed-sessions')
    allowed.update(('normal-admission.json', 'normal-history'))
    pending = None if number == len(records) else checkpoint(records[-1])
    if pending:
        staged = 'pointer-' + pending['recordSha256'] + '.pending'
        allowed.add(staged)
        if (root / staged).exists():
            require(read(root / staged) == pending, 'CONTROL_PARTIAL_POINTER')
    require(all(path.name in allowed for path in root.iterdir()), 'CONTROL_UNKNOWN_ARTIFACT')


def closed_sessions(directory):
    """Consistency witnesses only; registrations never supply independent authority pins."""
    directory = checked(directory)
    require(directory.is_dir(), 'CONTROL_CLOSED_SESSION_LAYOUT')
    for count, path in enumerate(directory.iterdir(), 1):
        require(count <= 100000 and re.fullmatch(r'[a-f0-9]{64}\.json', path.name),
                'CONTROL_CLOSED_SESSION_LAYOUT')
        value = read(path)
        fields(value, 'formatVersion authorityRoot authoritySha256')
        version(value['formatVersion'])
        checksum(value['authoritySha256'])
        require(value['authoritySha256'] != '0' * 64 and type(value['authorityRoot']) is str
                and Path(value['authorityRoot']).is_absolute(), 'CONTROL_CLOSED_SESSION_LAYOUT')


def load(location, trusted_checkpoint=None, *, allow_initial_normal=False):
    identity = validate_identity(read(location.root / 'identity.json'), location, allow_initial_normal)
    records = history(location.root)
    from control_successor import verify_history
    verify_history(location.root, records)
    if records:
        require(records[0]['commandSha256'] == identity['initialCommandSha256'], 'CONTROL_BOOTSTRAP_MISMATCH')
        require(records[0]['installationId'] == location.installation_id, 'CONTROL_INSTALLATION_MISMATCH')
    if trusted_checkpoint is not None:
        anchor(trusted_checkpoint, records)
    number = current(location.root, records)
    layout(location.root, records, number)
    return records, number, identity
