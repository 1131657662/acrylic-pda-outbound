"""Permanent writer lock protocol; control commands never repair missing originals."""
from contextlib import contextmanager
import os
import threading
from control_disk import read, lock_byte
from control_format import fields, version, hashed
from control_paths import file
from safety import OpsError, require
from control_writer_file import identity as file_identity

_LOCAL = threading.Lock()


def reset_local():
    global _LOCAL
    _LOCAL = threading.Lock()


if hasattr(os, 'register_at_fork'):
    os.register_at_fork(after_in_child=reset_local)


def descriptor(identity, root):
    return {'formatVersion': 2, 'installationId': identity['installationId'],
            'identitySha256': hashed(identity), 'fileIdentity': file_identity(root / 'writers.lock')}


def validate(root, identity, required=False):
    marker, lock = root / 'writers.json', root / 'writers.lock'
    if not marker.exists() and not lock.exists():
        require(not required, 'CONTROL_WRITER_PROTOCOL_REQUIRED')
        return False
    value = read(marker)
    fields(value, 'formatVersion installationId identitySha256 fileIdentity')
    require(type(value['formatVersion']) is int and value['formatVersion'] in (1, 2), 'CONTROL_FORMAT_UNSUPPORTED')
    require(not required or value['formatVersion'] == 2, 'CONTROL_ADMISSION_PROTOCOL_REQUIRED')
    expected = descriptor(identity, root)
    expected['formatVersion'] = value['formatVersion']
    require(value['fileIdentity'] == expected['fileIdentity'], 'CONTROL_WRITER_LOCK_REPLACED')
    require(value == expected, 'CONTROL_WRITER_PROTOCOL_MISMATCH')
    require(file(lock).stat().st_size == 1, 'CONTROL_WRITER_LOCK_INVALID')
    return True


@contextmanager
def exclusive(root, identity):
    # Caller holds coordinator first. Never open a second descriptor in this process.
    require(_LOCAL.acquire(blocking=False), 'CONTROL_WRITERS_ACTIVE')
    try:
        validate(root, identity, required=True)
        with file(root / 'writers.lock').open('r+b', buffering=0) as stream:
            require(os.fstat(stream.fileno()).st_size == 1, 'CONTROL_WRITER_LOCK_INVALID')
            try:
                lock_byte(stream, True)
            except OSError:
                raise OpsError('CONTROL_WRITERS_ACTIVE') from None
            try:
                validate(root, identity, required=True)
                yield
            finally:
                lock_byte(stream, False)
    finally:
        _LOCAL.release()
