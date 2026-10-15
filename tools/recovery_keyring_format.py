"""Strict pinned manifests for offline historical verification; no runtime key selection."""
from datetime import date
import hashlib
from pathlib import Path
import re
import uuid

from recovery_rekey import checked_file, external
from recovery_rekey_inspect import FIELDS, directory
from recovery_rekey_verify import canonical, read, require


def sha(value):
    require(isinstance(value, str) and re.fullmatch('[0-9a-f]{64}', value), 'KEYRING_SHA256')
    return value


def pinned(path, expected):
    path = checked_file(path)
    value = read(path)
    require(hashlib.sha256(canonical(value)).hexdigest() == sha(expected), 'KEYRING_PIN')
    return path, value


def schema(value, fields):
    require(type(value) is dict and set(value) == fields, 'KEYRING_FIELDS')


def header(value, fields, tag, expected):
    schema(value, fields)
    require(type(value['formatVersion']) is int and value['formatVersion'] == 1
            and value[tag] == expected, 'KEYRING_FORMAT')


def items(value):
    require(type(value) is list and 0 < len(value) <= 100, 'KEYRING_LIMIT')
    return value


def retention(entry):
    start, until = entry['retainedFrom'], entry['minimumRetainUntil']
    require(type(start) is str and type(until) is str, 'KEYRING_RETENTION_DATE')
    first, last = date.fromisoformat(start), date.fromisoformat(until)
    require(first.isoformat() == start and last.isoformat() == until, 'KEYRING_RETENTION_DATE')
    try:
        floor = first.replace(year=first.year + 5)
    except ValueError:
        floor = date(first.year + 5, 3, 1)
    require(last >= floor, 'KEYRING_RETENTION_FLOOR')


def key_entries(path, value):
    header(value, {'formatVersion', 'purpose', 'keys'}, 'purpose', 'HISTORICAL_VERIFY_ONLY')
    result = {}
    for entry in items(value['keys']):
        schema(entry, {'keyId', 'file', 'retainedFrom', 'minimumRetainUntil'})
        key_id = sha(entry['keyId'])
        require(key_id not in result and entry['file'] == key_id + '.bin', 'KEYRING_KEY_ENTRY')
        retention(entry)
        result[key_id] = {**entry, 'path': checked_file(path.parent / entry['file'])}
    require({p.name for p in path.parent.iterdir()} == {path.name, *(e['file'] for e in result.values())},
            'KEYRING_LAYOUT')
    return result


def reference_entries(value):
    header(value, {'formatVersion', 'operation', 'references'}, 'operation', 'VERIFY_P5_REFERENCES')
    entries = items(value['references'])
    pins, requests, backups = set(), set(), set()
    for entry in entries:
        schema(entry, {'requestFile', 'requestSha256', 'backupDirectory', 'inventorySha256'})
        pin = sha(entry['inventorySha256']); sha(entry['requestSha256'])
        request, backup = checked_file(Path(entry['requestFile'])), directory(Path(entry['backupDirectory']))
        require(pin not in pins and request not in requests and backup not in backups, 'KEYRING_DUPLICATE_REFERENCE')
        pins.add(pin); requests.add(request); backups.add(backup)
    return entries


def request_input(entry, external_paths):
    path, request = pinned(Path(entry['requestFile']), entry['requestSha256'])
    header(request, FIELDS, 'operation', 'REKEY_P5')
    require(type(request['restoreRoots']) is list and request['restoreRoots'], 'KEYRING_RESTORE_ROOTS')
    roots = [request[k] for k in ('authorityRoot', 'leaseRoot', 'closedAuthority')] + request['restoreRoots']
    for root in roots:
        require(type(root) is str and Path(root).is_absolute() and '..' not in Path(root).parts,
                'KEYRING_ROOT_PATH')
    require(sha(request['oldAnchorId']) != sha(request['newAnchorId']), 'KEYRING_SAME_KEY')
    sha(request['identitySha256']); sha(request['markerSha256'])
    require(str(uuid.UUID(request['installationId'])) == request['installationId'], 'KEYRING_INSTALLATION')
    for other in (path, Path(entry['backupDirectory']), *external_paths):
        external(other, request)
    return request
