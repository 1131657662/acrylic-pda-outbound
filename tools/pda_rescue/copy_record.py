"""Reverify encrypted rescue snapshots; a copy never grants business or deletion authority."""
from datetime import datetime, timezone
import hashlib
from pathlib import Path

from .envelope import decrypt, MAX_BYTES, HEADER_SIZE
from .integrity import parse
from .verify import verify

FORMAT = 'PDA_ENCRYPTED_COPY_V1'


def regular(path: Path) -> Path:
    path = path.absolute()
    if any(part.is_symlink() or part.is_junction() for part in (path, *path.parents)):
        raise ValueError('Copy paths cannot traverse links')
    if not path.is_file() or path.stat().st_nlink != 1:
        raise ValueError('Copy input must be one regular file')
    return path


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def verified(package: Path, password: str, trust: Path) -> tuple[bytes, dict]:
    package, trust = regular(package), regular(trust)
    if package.stat().st_size > MAX_BYTES + HEADER_SIZE + 16 or trust.stat().st_size > 1024 * 1024:
        raise ValueError('Copy input exceeds resource limit')
    encrypted = package.read_bytes()
    trusted = trust.read_bytes()
    result = verify(decrypt(package, password), parse(trusted.removeprefix(b'\xef\xbb\xbf')))
    if not result['sourceTrusted'] or package.read_bytes() != encrypted or trust.read_bytes() != trusted:
        raise ValueError('Source must remain unchanged and externally trusted')
    order = result['facts']['order']
    snapshot = {'packageBytes': str(len(encrypted)), 'packageSha256': digest(encrypted),
        'trustSha256': digest(trusted), 'manifestSha256': digest(result['files']['manifest.json']),
        'exportId': result['manifest']['exportId'], 'deviceKeySha256': result['manifest']['deviceKeySha256'],
        'scope': {key: order[key] for key in ('orderId', 'localOrderId', 'source', 'operatorId', 'deviceId', 'warehouseId')},
        'summary': result['manifest']['summary']}
    return encrypted, snapshot


def custody(value: dict) -> dict:
    if set(value) != {'custodian', 'location'}:
        raise ValueError('Custodian and storage location must be explicitly recorded')
    for text in value.values():
        if not isinstance(text, str) or not 1 <= len(text.strip()) <= 200 or any(ord(c) < 32 for c in text):
            raise ValueError('Custody labels must be bounded, nonempty plain text')
    return dict(value)


def record(snapshot: dict, labels: dict) -> dict:
    return {'format': FORMAT, 'createdAt': datetime.now(timezone.utc).isoformat(),
        'snapshot': snapshot, 'custody': custody(labels), 'sourceTrusted': True,
        'targetReadBackVerified': True, 'externalStorageVerified': False,
        'protectsExportedSnapshotOnly': True, 'businessReceiptVerified': False,
        'localDeletionAllowed': False}


def checked_record(data: bytes) -> dict:
    if len(data) > 65536:
        raise ValueError('Copy record exceeds resource limit')
    value = parse(data)
    expected = {'format', 'createdAt', 'snapshot', 'custody', 'sourceTrusted', 'targetReadBackVerified',
        'externalStorageVerified', 'protectsExportedSnapshotOnly', 'businessReceiptVerified', 'localDeletionAllowed'}
    if set(value) != expected or value['format'] != FORMAT:
        raise ValueError('Unknown copy record')
    custody(value['custody'])
    for name, required in {'sourceTrusted': True, 'targetReadBackVerified': True, 'externalStorageVerified': False,
        'protectsExportedSnapshotOnly': True, 'businessReceiptVerified': False, 'localDeletionAllowed': False}.items():
        if value[name] is not required:
            raise ValueError('Copy record cannot grant business or external-storage authority')
    return value
