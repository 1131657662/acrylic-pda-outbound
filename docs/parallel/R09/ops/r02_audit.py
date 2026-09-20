"""Read-only audit of existing R02 backups; no service, timer or database writes."""
from datetime import datetime, timezone
import hashlib
from pathlib import Path, PurePosixPath
import re
import tarfile
from archive import gzip_check, MAX_EXPANDED
from safety import regular, require, sha, read_json, child

NAMES = {'database.sql.gz', 'database-facts.json', 'private-files-and-config.tar.gz', 'release.txt', 'manifest.json'}
SERVICES = {'acrylic-outbound-test.service', 'acrylic-outbound-test-worker.service'}
CONFIG = 'etc/acrylic-outbound-test/'
FILES = 'var/lib/acrylic-outbound-test/files/'
REQUIRED = {'runtime.env', 'migration.env', 'migration.cnf', 'signing.pk8', 'signing.der'}


def verify_r02(path):
    path = Path(path)
    require(path.is_dir() and not path.is_symlink() and not path.is_junction(), 'INVALID_BACKUP_DIRECTORY')
    require(bool(re.fullmatch(r'\d{8}T\d{6}Z', path.name)), 'INVALID_R02_BACKUP_TIME')
    captured = datetime.strptime(path.name, '%Y%m%dT%H%M%SZ').replace(tzinfo=timezone.utc).timestamp()
    seen = set()
    for line in regular(path / 'SHA256SUMS').read_text(encoding='ascii').splitlines():
        checksum, name = line.split('  ', 1)
        require(name in NAMES | {'active-services.txt'} and name not in seen, 'CHECKSUM_MEMBER_SET_MISMATCH')
        require(sha(regular(path / name)) == checksum, 'BACKUP_CHECKSUM_MISMATCH')
        seen.add(name)
    require(seen in (NAMES, NAMES | {'active-services.txt'}), 'BACKUP_COMPONENT_MISSING')
    checkpoint = service_checkpoint(path, seen)
    return inspect_components(path, captured, checkpoint)


def service_checkpoint(path, seen):
    if 'active-services.txt' not in seen:
        require(not (path / 'active-services.txt').exists(), 'UNCHECKSUMMED_SERVICE_CHECKPOINT')
        return 'legacy-api-only'
    lines = regular(path / 'active-services.txt').read_text(encoding='ascii').splitlines()
    if lines == ['']:  # bash printf '%s\n' with an empty array emits one newline.
        lines = []
    require(set(lines) <= SERVICES and len(lines) == len(set(lines)), 'INVALID_SERVICE_CHECKPOINT')
    return 'api-worker-checkpoint'


def inspect_components(path, captured, checkpoint):
    facts = read_json(path / 'database-facts.json')
    require(bool(facts), 'EMPTY_SCHEMA')
    for table, info in facts.items():
        require(bool(re.fullmatch('[a-zA-Z0-9_]+', table)), 'BAD_SQL_IDENTIFIER')
        require(isinstance(info['count'], int) and info['count'] >= 0, 'INVALID_TABLE_COUNT')
        require(info['checksum'] not in (None, 'NULL'), 'UNSUPPORTED_TABLE_CHECKSUM')
    gzip_check(path / 'database.sql.gz')
    files = inspect_tar(path / 'private-files-and-config.tar.gz')
    return {'captured_timestamp': captured, 'tables': len(facts), 'files': files, 'writer_checkpoint': checkpoint,
            'scope': 'legacy archive integrity; actual restore remains separate'}


def inspect_tar(path):
    gzip_check(path)
    seen, config = set(), set()
    size, files = 0, 0
    with tarfile.open(path, 'r:gz') as archive:
        for member in archive:
            name = member.name.rstrip('/')
            child(Path.cwd(), name)
            require(name not in seen, 'DUPLICATE_ARCHIVE_MEMBER')
            seen.add(name)
            require(member.isdir() or member.isfile(), 'ARCHIVE_SPECIAL_MEMBER')
            require(not member.issparse(), 'ARCHIVE_SPECIAL_MEMBER')
            require(name.startswith(CONFIG) or name.startswith(FILES) or name in (CONFIG[:-1], FILES[:-1]), 'ARCHIVE_ROOT_NOT_ALLOWED')
            if member.isdir():
                continue
            size += member.size
            require(size <= MAX_EXPANDED, 'ARCHIVE_SIZE_LIMIT')
            with archive.extractfile(member) as stream:
                hashlib.file_digest(stream, 'sha256')
            if name.startswith(CONFIG):
                config.add(name[len(CONFIG):])
            files += 1
    require(REQUIRED <= config, 'SIGNING_CONFIGURATION_INCOMPLETE')
    return files
