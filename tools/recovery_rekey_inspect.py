"""Read-only interrupted-rekey diagnosis. Every result requires writers to remain stopped."""
import argparse
import hashlib
from pathlib import Path
import sys
from recovery_rekey import checked_file, external, key_file
from recovery_rekey_verify import canonical, digest, read, require, verify_inventory
from recovery_rekey_inspect_state import inventory, originals, classify

FIELDS = {'formatVersion', 'operation', 'installationId', 'authorityRoot', 'leaseRoot', 'closedAuthority',
          'identitySha256', 'markerSha256', 'oldAnchorId', 'newAnchorId', 'restoreRoots', 'retainedAnchorFile'}


def directory(path):
    require(path.is_absolute() and path.is_dir(), 'REKEY_INSPECT_DIRECTORY')
    for item in (path, *path.parents):
        require(not item.is_symlink() and not item.is_junction(), 'REKEY_INSPECT_PATH_REDIRECTION')
    return path.resolve(strict=True)


def inputs(args):
    request_file = checked_file(args.request)
    request = read(request_file)
    require(set(request) == FIELDS and type(request['formatVersion']) is int and
            request['formatVersion'] == 1 and request['operation'] == 'REKEY_P5', 'REKEY_INSPECT_REQUEST')
    external(request_file, request)
    root = directory(Path(request['leaseRoot']))
    backup = directory(args.backup)
    require(backup.parent == root.parent and backup.name.startswith('.rekey-backup-'), 'REKEY_INSPECT_BACKUP_PATH')
    authority = directory(Path(request['authorityRoot']))
    require(digest(read(checked_file(authority / 'identity.json'))) == request['identitySha256'],
            'REKEY_INSPECT_AUTHORITY_IDENTITY')
    return request, root, backup, authority


def inspect(args):
    request, root, backup, authority = inputs(args)
    old, new, retained = bytearray(), bytearray(), bytearray()
    try:
        old = key_file(args.current_key_file, request)
        new = key_file(args.next_key_file, request)
        retained = key_file(Path(request['retainedAnchorFile']), request)
        require(old == retained, 'REKEY_INSPECT_RETAINED_KEY')
        for name, key in (('oldAnchorId', old), ('newAnchorId', new)):
            require(hashlib.sha256(key).hexdigest() == request[name], 'REKEY_INSPECT_KEY_ID')
        require(old != new, 'REKEY_INSPECT_SAME_KEY')
        before = {str(path): inventory(path) for path in (root, backup, authority)}
        expected = originals(request, backup, old, new)
        set_authenticated = verify_inventory(request, backup, (old, new))
        state, counts = classify(root, expected)
        after = {str(path): inventory(path) for path in (root, backup, authority)}
        require(before == after, 'REKEY_INSPECT_CONCURRENT_CHANGE')
        return {'status': 'INSPECTED', 'state': state, 'counts': counts, 'snapshotUnchanged': True,
                'processSetAuthenticated': set_authenticated, 'writerLivenessVerified': False,
                'recoveryAllowed': False, 'keepWritersStopped': True, 'productionApproved': False}
    finally:
        for key in (old, new, retained):
            key[:] = bytes(len(key))


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('request', 'backup', 'current-key-file', 'next-key-file'):
        parser.add_argument('--' + name, required=True, type=Path)
    return parser.parse_args()


def main():
    try:
        report = inspect(arguments())
        sys.stdout.buffer.write(canonical(report)); sys.stdout.buffer.flush()
        return 0
    except (OSError, ValueError, KeyError, TypeError, AttributeError):
        sys.stdout.buffer.write(canonical({'status': 'UNKNOWN', 'recoveryAllowed': False,
                                          'processSetAuthenticated': False, 'writerLivenessVerified': False,
                                          'keepWritersStopped': True, 'productionApproved': False}))
        sys.stdout.buffer.flush()
        return 3


if __name__ == '__main__':
    sys.exit(main())
