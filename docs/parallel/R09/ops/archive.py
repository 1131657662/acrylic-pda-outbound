"""Digest verification and extraction without links, traversal or overwrites."""
import gzip
import hashlib
from pathlib import Path
import shutil
import tarfile
from safety import child, regular, require, sha

MAX_EXPANDED = 8 * 1024**3


def gzip_check(path):
    total = 0
    with gzip.open(regular(path), 'rb') as stream:
        while chunk := stream.read(1024**2):
            total += len(chunk)
            require(total <= MAX_EXPANDED, 'ARCHIVE_SIZE_LIMIT')
    require(total > 0, 'EMPTY_COMPRESSED_FILE')


def pack(destination, roots):
    with tarfile.open(destination, 'x:gz') as archive:
        for prefix, root in roots.items():
            for path in sorted(Path(root).rglob('*')):
                if path.is_file():
                    regular(path)
                    archive.add(path, arcname=prefix + '/' + path.relative_to(root).as_posix(), recursive=False)


def members_checked(archive, expected):
    seen = set()
    total = 0
    for member in archive:
        require(member.isfile() and not member.issparse(), 'ARCHIVE_SPECIAL_MEMBER')
        name = member.name
        child(Path.cwd(), name)  # Validate portable relative syntax before any extraction.
        require(name.casefold() not in seen and name in expected, 'ARCHIVE_MEMBER_SET_MISMATCH')
        seen.add(name.casefold())
        total += member.size
        require(0 <= member.size <= MAX_EXPANDED and total <= MAX_EXPANDED, 'ARCHIVE_SIZE_LIMIT')
        require(member.size == expected[name]['bytes'], 'ARCHIVE_MEMBER_SIZE_MISMATCH')
        yield member
    require(len(seen) == len(expected), 'ARCHIVE_MEMBER_MISSING')


def verify_archive(path, expected):
    gzip_check(path)
    with tarfile.open(path, 'r:gz') as archive:
        for member in members_checked(archive, expected):
            with archive.extractfile(member) as stream:
                actual = hashlib.file_digest(stream, 'sha256').hexdigest()
            require(actual == expected[member.name]['sha256'], 'ARCHIVE_MEMBER_HASH_MISMATCH')


def extract(path, expected, destination):
    require(not destination.exists(), 'RESTORE_FILES_ALREADY_EXIST')
    verify_archive(path, expected)
    destination.mkdir(mode=0o700)
    with tarfile.open(path, 'r:gz') as archive:
        for member in members_checked(archive, expected):
            target = child(destination, member.name)
            target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            with archive.extractfile(member) as stream, target.open('xb') as output:
                shutil.copyfileobj(stream, output)
            target.chmod(0o600)
            require(sha(target) == expected[member.name]['sha256'], 'EXTRACTED_FILE_HASH_MISMATCH')
