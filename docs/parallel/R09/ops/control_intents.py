"""Retained admission intent chain. Completion is proved by original control history."""
from control_disk import read
from control_format import fields, version, hashed, ZERO_HASH
from control_model import command, transition
from control_paths import checked
from safety import require

FIELDS = 'formatVersion installationId targetRevision previousRecordSha256 command commandSha256 intentSha256'
HEAD_FIELDS = 'formatVersion installationId targetRevision intentSha256'


def name(number):
    return f'{number:018d}.json'


def head(intent):
    return {key: intent[key] for key in HEAD_FIELDS.split()}


def initial(installation):
    return {'formatVersion': 1, 'installationId': installation,
            'targetRevision': '1', 'intentSha256': ZERO_HASH}


def create(previous, value):
    command(value)
    transition(previous, value)
    body = {'formatVersion': 1, 'installationId': value['installationId'],
            'targetRevision': str(int(previous['revision']) + 1),
            'previousRecordSha256': previous['recordSha256'], 'command': value,
            'commandSha256': hashed(value)}
    return {**body, 'intentSha256': hashed(body)}


def validate(value, previous):
    fields(value, FIELDS)
    version(value['formatVersion'])
    require(value == create(previous, value['command']), 'CONTROL_INTENT_CORRUPT')
    return value


def inspect(root, records, complete=True):
    directory = checked(root / 'intents')
    require(directory.is_dir(), 'CONTROL_INTENTS_MISSING')
    paths = sorted(directory.iterdir())
    require(len(paths) <= 100000, 'CONTROL_INTENT_LIMIT')
    identity = read(root / 'identity.json')['installationId']
    heads, intents = [initial(identity)], []
    for number, path in enumerate(paths, 2):
        require(path.name == name(number) and number <= len(records) + 1, 'CONTROL_INTENT_GAP')
        value = validate(read(path), records[number - 2])
        if number <= len(records):
            require(value['command'] == records[number - 1]['command'], 'CONTROL_INTENT_RECORD_CONFLICT')
        intents.append(value)
        heads.append(head(value))
    marker = read(root / 'admission.json')
    fields(marker, HEAD_FIELDS)
    version(marker['formatVersion'])
    require(marker in heads[-2:], 'CONTROL_INTENT_HEAD_CONFLICT')
    require(len(intents) >= max(0, len(records) - 1), 'CONTROL_INTENT_GAP')
    pending = intents[-1] if intents and int(intents[-1]['targetRevision']) > len(records) else None
    if pending:
        from control_successor import check_pending
        check_pending(root, records, pending['command'])
    staged = root / ('admission-' + heads[-1]['intentSha256'] + '.pending')
    if staged.exists():
        require(read(staged) == heads[-1] and marker != heads[-1], 'CONTROL_INTENT_HEAD_CONFLICT')
    if complete:
        require(marker == heads[-1], 'CONTROL_INTENT_INCOMPLETE')
    return intents, marker, heads[-1], pending
