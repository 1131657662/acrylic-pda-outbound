"""Explicit original-byte restore supplier. Every outcome keeps writers stopped."""
import argparse
import hashlib
from pathlib import Path
import sys
from recovery_rekey import checked_file, external, key_file, command, invoke
from recovery_rekey_inspect import directory, inspect
from recovery_rekey_verify import canonical, digest, read, require, INVENTORY_FILE


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('java', 'classpath', 'request', 'backup', 'current-key-file', 'next-key-file', 'evidence-dir'):
        parser.add_argument('--' + name, required=True, type=Path)
    parser.add_argument('--inventory-sha256', required=True)
    return parser.parse_args()


def verify_restore(args, request, evidence):
    receipt = read(evidence / 'stdout.json')
    require(receipt['status'] == 'ORIGINALS_RESTORED' and receipt['runtimeUpdated'] is False
            and receipt['keepWritersStopped'] is True and receipt['recoveryAllowed'] is False
            and receipt['productionApproved'] is False, 'REKEY_RESTORE_RECEIPT')
    require(receipt['inventorySha256'] == args.inventory_sha256 == digest(read(args.backup / INVENTORY_FILE)),
            'REKEY_RESTORE_PIN')
    report = inspect(args)
    require(report['state'] == 'ALL_OLD' and report['processSetAuthenticated'] is True
            and receipt['markerSha256'] == request['markerSha256'], 'REKEY_RESTORE_READBACK')
    result = {**report, 'originalBytesRestored': True, 'runtimeUpdated': False,
              'alreadyOriginal': receipt['alreadyOriginal'], 'restoreEvidenceDirectory': receipt['evidenceDirectory']}
    (evidence / 'verified.json').write_bytes(canonical(result))
    sys.stdout.buffer.write(canonical(result)); sys.stdout.buffer.flush()


def execute(args):
    args.request = checked_file(args.request); args.backup = directory(args.backup)
    request = read(args.request); external(args.request, request)
    require(len(args.inventory_sha256) == 64 and all(ch in '0123456789abcdef' for ch in args.inventory_sha256),
            'REKEY_RESTORE_PIN')
    require(digest(read(checked_file(args.backup / INVENTORY_FILE))) == args.inventory_sha256, 'REKEY_RESTORE_PIN')
    evidence = args.evidence_dir
    require(evidence.is_absolute() and not evidence.exists(), 'REKEY_RESTORE_NEW_EVIDENCE')
    external(evidence.resolve(), request); directory(evidence.parent); evidence.mkdir()
    old, new, pipe = bytearray(), bytearray(), bytearray()
    try:
        old = key_file(args.current_key_file, request); new = key_file(args.next_key_file, request); pipe = old + new
        cmd = command(args); cmd[4] = 'com.acrylic.outbound.recovery.RecoveryRekeyRestoreCli'
        cmd += ['--backup', str(args.backup), '--inventory-sha256', args.inventory_sha256]
        (evidence / 'invocation.json').write_bytes(canonical({'command': cmd,
            'requestSha256': hashlib.sha256(args.request.read_bytes()).hexdigest(), 'inventorySha256': args.inventory_sha256}))
        status = invoke(cmd, pipe, evidence)
        (evidence / 'exit.json').write_bytes(canonical({'exit': status, 'automaticRetry': False}))
        if status:
            return status
        verify_restore(args, request, evidence)
        return 0
    finally:
        for key in (old, new, pipe):
            key[:] = bytes(len(key))


def main():
    try:
        return execute(arguments())
    except (OSError, ValueError, KeyError, TypeError, AttributeError):
        sys.stderr.write('Original-byte restore not verified; preserve all evidence and keep writers stopped.\n')
        return 3


if __name__ == '__main__':
    sys.exit(main())
