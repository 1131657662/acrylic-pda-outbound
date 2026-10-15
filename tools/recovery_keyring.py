"""Read-only audit of explicitly pinned historical keys and a fixed recovery-reference catalog."""
import argparse
import hashlib
from pathlib import Path
import sys

from recovery_keyring_format import pinned, key_entries, reference_entries, request_input, schema
from recovery_rekey import checked_file
from recovery_rekey_inspect_state import inventory, originals
from recovery_rekey_verify import canonical, read, require, verify_inventory, INVENTORY_FILE


BOUNDARY = {'keepWritersStopped': True, 'recoveryAllowed': False, 'productionApproved': False,
            'runtimeUpdated': False, 'deletionAllowed': False, 'writerLivenessVerified': False,
            'writeProtectionVerified': False, 'externalCustodyVerified': False,
            'globalReferenceCoverageVerified': False}


def material(entries):
    keys = {}
    try:
        for key_id, entry in entries.items():
            with entry['path'].open('rb') as stream:
                key = bytearray(stream.read(33))
            keys[key_id] = key
            require(len(key) == 32 and hashlib.sha256(key).hexdigest() == key_id, 'KEYRING_KEY_ID')
        return keys
    except BaseException:
        erase(keys)
        raise


def erase(keys):
    for key in keys.values():
        key[:] = bytes(len(key))


def snapshot(keyring, catalog, references):
    files = [keyring, catalog, *(Path(e['requestFile']) for e in references)]
    result = {str(p): hashlib.sha256(checked_file(p).read_bytes()).hexdigest() for p in files}
    for root in (keyring.parent, *(Path(e['backupDirectory']) for e in references)):
        result[str(root)] = inventory(root)
    return result


def snapshot_pins(before, keyring, catalog, args, entries, references):
    require(before[str(keyring)] == args.keyring_sha256 and before[str(catalog)] == args.catalog_sha256,
            'KEYRING_CONCURRENT_CHANGE')
    expected_keys = {keyring.name, *(entry['file'] for entry in entries.values())}
    require(set(before[str(keyring.parent)]) == expected_keys, 'KEYRING_LAYOUT')
    for key_id, entry in entries.items():
        require(before[str(keyring.parent)][entry['file']] == key_id, 'KEYRING_KEY_ID')
    for entry in references:
        require(before[str(Path(entry['requestFile']))] == entry['requestSha256']
                and before[str(Path(entry['backupDirectory']))].get(INVENTORY_FILE) == entry['inventorySha256'],
                'KEYRING_CONCURRENT_CHANGE')


def original_format(backup):
    marker = read(checked_file(backup / 'lease-root.json'))
    index = read(checked_file(backup / 'leases/index.json'))
    schema(marker, {'formatVersion', 'installationId', 'authorityRoot', 'leaseRoot', 'identitySha256',
                    'anchorId', 'fenceFileIdentity', 'coordinatorFileIdentity', 'bindingSha256'})
    schema(index, {'formatVersion', 'installationId', 'entries', 'indexTag'})
    for value in (marker, index):
        require(type(value['formatVersion']) is int and value['formatVersion'] == 1, 'KEYRING_RECORD_FORMAT')


def verify_reference(entry, request, keys):
    backup = Path(entry['backupDirectory'])
    pinned(backup / INVENTORY_FILE, entry['inventorySha256'])
    old, new = (keys[request[name]] for name in ('oldAnchorId', 'newAnchorId'))
    require(verify_inventory(request, backup, (old, new)), 'KEYRING_INVENTORY_REQUIRED')
    original_format(backup)
    expected = originals(request, backup, old, new)
    return {'inventorySha256': entry['inventorySha256'], 'requestSha256': entry['requestSha256'],
            'oldAnchorId': request['oldAnchorId'], 'newAnchorId': request['newAnchorId'],
            'processRecords': len(expected) - 2, 'processSetAuthenticated': True,
            'originalSignaturesVerified': True}


def coverage(requests, entries):
    counts = {key_id: 0 for key_id in entries}
    for request in requests:
        for name in ('oldAnchorId', 'newAnchorId'):
            require(request[name] in counts, 'KEYRING_REFERENCED_KEY_MISSING')
            counts[request[name]] += 1
    return [{'keyId': key_id, 'referenceCount': counts[key_id],
             'minimumRetainUntil': entry['minimumRetainUntil'], 'deletionAllowed': False}
            for key_id, entry in sorted(entries.items())]


def audit(args):
    keyring, manifest = pinned(args.keyring, args.keyring_sha256)
    catalog, reference_catalog = pinned(args.catalog, args.catalog_sha256)
    require(not catalog.is_relative_to(keyring.parent), 'KEYRING_INDEPENDENT_CATALOG')
    entries = key_entries(keyring, manifest)
    references = reference_entries(reference_catalog)
    outside = (keyring.parent, catalog)
    requests = [request_input(entry, outside) for entry in references]
    key_reports = coverage(requests, entries)
    before = snapshot(keyring, catalog, references)
    snapshot_pins(before, keyring, catalog, args, entries, references)
    keys = material(entries)
    try:
        reports = [verify_reference(entry, request, keys) for entry, request in zip(references, requests)]
        require(before == snapshot(keyring, catalog, references), 'KEYRING_CONCURRENT_CHANGE')
        return {**BOUNDARY, 'status': 'VERIFIED', 'catalogCoverageVerified': True,
                'snapshotUnchanged': True, 'keyringSha256': args.keyring_sha256,
                'catalogSha256': args.catalog_sha256, 'keys': key_reports, 'references': reports}
    finally:
        erase(keys)


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('keyring', 'catalog'):
        parser.add_argument('--' + name, type=Path, required=True)
        parser.add_argument('--' + name + '-sha256', required=True)
    return parser.parse_args()


def main():
    try:
        report = audit(arguments())
        status = 0
    except (OSError, ValueError, KeyError, TypeError, AttributeError):
        report = {**BOUNDARY, 'status': 'UNKNOWN', 'catalogCoverageVerified': False}
        status = 3
    sys.stdout.buffer.write(canonical(report)); sys.stdout.buffer.flush()
    return status


if __name__ == '__main__':
    sys.exit(main())
