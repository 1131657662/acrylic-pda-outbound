#!/usr/bin/env python3
"""Check the immutable backend package before maintenance can stop writers."""
import hashlib
import json
from pathlib import Path
import re
import sys

REQUIRED = {
    'server.jar', 'source-sha256.json', 'deploy/verify_backend_release.py',
    'deploy/activate_backend.sh', 'deploy/migrate.sh', 'deploy/backup.sh',
    'deploy/service_checkpoint.sh', 'deploy/backup_facts.py',
    'deploy/acrylic-outbound-test.service', 'deploy/acrylic-outbound-test-worker.service',
    'deploy/acrylic-outbound-test-backup.service', 'deploy/acrylic-outbound-test-backup.timer',
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def verify(root):
    if root.is_symlink() or not root.is_dir():
        raise ValueError('Release must be a real directory')
    manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
    require(manifest['release'] == root.name, 'Release ID mismatch')
    require(manifest['environment'] == 'independent-test', 'Environment mismatch')
    require(manifest['contract'] == '0.30.0' and manifest['flyway'] == 34, 'Unsupported contract/schema')
    hashes = manifest['artifactHashes']
    require(REQUIRED <= hashes.keys(), 'Missing deployment artifact')
    for relative, expected in hashes.items():
        path = root / relative
        require(not Path(relative).is_absolute() and '..' not in Path(relative).parts, 'Unsafe artifact path')
        require(not path.is_symlink() and root.resolve() in path.resolve().parents, 'Artifact escapes release')
        require(re.fullmatch(r'[0-9a-f]{64}', expected), 'Invalid SHA-256')
        require(hashlib.sha256(path.read_bytes()).hexdigest() == expected, f'Artifact mismatch: {relative}')
    require(hashes['server.jar'] == manifest['serverSha256'], 'Server hash mismatch')
    print('Verified backend package: ' + root.name)


if __name__ == '__main__':
    verify(Path(sys.argv[1]))
