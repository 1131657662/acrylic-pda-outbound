"""Publish a durable stop intent before draining writers; resume only its original command."""
from control_disk import append, replace, sync_directory
from control_format import canonical, hashed
from control_intents import create, head, initial, inspect, name
from safety import require


def bootstrap(root, installation):
    (root / 'intents').mkdir(mode=0o700)
    append(root / 'admission.json', canonical(initial(installation)))
    sync_directory(root)


def prepare(root, records, value, fault):
    intents, marker, latest, pending = inspect(root, records, complete=False)
    selected = pending or (intents[-1] if intents else None)
    match = next((row for row in records if row['command']['commandId'] == value['commandId']), None)
    if match:
        require(match['commandSha256'] == hashed(value), 'CONTROL_COMMAND_CONFLICT')
    if pending:
        require(pending['command'] == value, 'CONTROL_INTENT_CONFLICT')
    if not match and not pending:
        require(marker == latest, 'CONTROL_INTENT_INCOMPLETE')
        intent = create(records[-1], value)
        selected = intent
        append(root / 'intents' / name(int(intent['targetRevision'])), canonical(intent))
        fault('intent_appended')
        latest = head(intent)
    if marker != latest:
        require(selected is not None and selected['command'] == value, 'CONTROL_INTENT_CONFLICT')
        publish(root, latest, fault)


def publish(root, marker, fault):
    staged = root / ('admission-' + marker['intentSha256'] + '.pending')
    if not staged.exists():
        append(staged, canonical(marker))
    fault('intent_head_staged')
    replace(staged, root / 'admission.json')
    fault('intent_head_committed')
