"""Compare both generations without treating an unsigned file set as recovery authority."""
import hashlib
import hmac
from pathlib import Path
import re
import uuid
from recovery_rekey import checked_file
from recovery_rekey_verify import authenticated, canonical, digest, read, require, INVENTORY_FILE

PROCESS_FIELDS = {'formatVersion', 'installationId', 'processIdentity', 'identitySha256',
                  'pid', 'jvmVersion', 'createdAt', 'anchorTag'}


def inventory(root):
    result = {}
    require(root.is_dir(), 'REKEY_INSPECT_DIRECTORY')
    for path in sorted(root.rglob('*')):
        require(not path.is_symlink() and not path.is_junction(), 'REKEY_INSPECT_PATH_REDIRECTION')
        if path.is_dir():
            continue
        file = checked_file(path)
        require(file.stat().st_size <= 65536, 'REKEY_INSPECT_FILE_SIZE')
        result[path.relative_to(root).as_posix()] = hashlib.sha256(file.read_bytes()).hexdigest()
        require(len(result) <= 10000, 'REKEY_INSPECT_FILE_LIMIT')
    return result


def signed(body, key, field):
    return {**body, field: hmac.digest(key, canonical(body), 'sha256').hex()}


def originals(request, backup, old_key, new_key):
    marker = read(checked_file(backup / 'lease-root.json'))
    require(digest(marker) == request['markerSha256'], 'REKEY_INSPECT_ORIGINAL_MARKER')
    for key in ('installationId', 'authorityRoot', 'leaseRoot', 'identitySha256'):
        require(marker[key] == request[key], 'REKEY_INSPECT_IDENTITY')
    require(marker['anchorId'] == request['oldAnchorId'], 'REKEY_INSPECT_ORIGINAL_ANCHOR')
    body = {key: value for key, value in marker.items() if key != 'bindingSha256'}
    require(digest(body) == marker['bindingSha256'], 'REKEY_INSPECT_ORIGINAL_BINDING')
    new_marker = {**body, 'anchorId': request['newAnchorId']}
    expected = {'lease-root.json': (marker, {**new_marker, 'bindingSha256': digest(new_marker)})}
    index = authenticated(checked_file(backup / 'leases/index.json'), old_key, 'indexTag')
    require(set(index) == {'formatVersion', 'installationId', 'entries'} and index['formatVersion'] == 1
            and index['installationId'] == request['installationId'] and index['entries'] == [],
            'REKEY_INSPECT_ACTIVE_LEASE')
    expected['leases/index.json'] = (signed(index, old_key, 'indexTag'), signed(index, new_key, 'indexTag'))
    processes(request, backup, old_key, new_key, expected)
    allowed = set(expected) | ({INVENTORY_FILE} if (backup / INVENTORY_FILE).exists() else set())
    require(set(inventory(backup)) == allowed, 'REKEY_INSPECT_BACKUP_LAYOUT')
    return expected


def processes(request, backup, old_key, new_key, expected):
    directory = backup / 'processes'
    for path in sorted(directory.iterdir()) if directory.exists() else []:
        require(path.is_file() and re.fullmatch(r'[0-9a-f-]{36}\.json', path.name), 'REKEY_INSPECT_PROCESS_LAYOUT')
        original = read(checked_file(path))
        require(set(original) == PROCESS_FIELDS, 'REKEY_INSPECT_PROCESS_FIELDS')
        body = authenticated(path, old_key)
        require(type(body['formatVersion']) is int and body['formatVersion'] == 1 and
                body['installationId'] == request['installationId'] and
                body['identitySha256'] == request['identitySha256'] and
                str(uuid.UUID(body['processIdentity'])) + '.json' == path.name,
                'REKEY_INSPECT_PROCESS_IDENTITY')
        expected['processes/' + path.name] = (original, signed(body, new_key, 'anchorTag'))


def classify(root, expected):
    files = inventory(root)
    allowed = {*expected, 'fenced.lock', *(name + '.rekey.pending' for name in expected)}
    require(set(files) <= allowed and set(expected) <= set(files), 'REKEY_INSPECT_CURRENT_LAYOUT')
    require((root / 'fenced.lock').read_bytes() == b'0', 'REKEY_INSPECT_FENCE_INVALID')
    counts = {'old': 0, 'new': 0, 'pending': 0}
    for name, (old, new) in expected.items():
        current = read(checked_file(root / name))
        require(current in (old, new), 'REKEY_INSPECT_UNTRUSTED_CONTENT')
        counts['old' if current == old else 'new'] += 1
        pending = root / (name + '.rekey.pending')
        if pending.exists():
            require(read(checked_file(pending)) in (old, new), 'REKEY_INSPECT_UNTRUSTED_PENDING')
            counts['pending'] += 1
    state = 'MIXED' if counts['old'] and counts['new'] else ('ALL_OLD' if counts['old'] else 'ALL_NEW')
    return ('PENDING' if counts['pending'] else state), counts
