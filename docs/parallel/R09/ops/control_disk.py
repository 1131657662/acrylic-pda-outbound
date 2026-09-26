"""Durable local file operations and process locks; never remove history."""
from contextlib import contextmanager
import os
import threading
from control_paths import checked, file
from control_format import canonical, decode, MAX_BYTES
from safety import OpsError, require

_PROCESS_LOCK = threading.Lock()


def reset_process_lock():
    global _PROCESS_LOCK
    _PROCESS_LOCK = threading.Lock()


if hasattr(os, 'register_at_fork'):
    os.register_at_fork(after_in_child=reset_process_lock)


def sync_directory(path):
    if os.name == 'nt':
        return  # Windows replacement uses WRITE_THROUGH; real power loss remains unverified.
    descriptor = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def append(path, data):
    checked(path)
    with path.open('xb') as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())
    sync_directory(path.parent)


def read(path):
    with file(path).open('rb') as stream:
        data = stream.read(MAX_BYTES + 1)
    return decode(data)


def replace(source, target):
    file(source)
    checked(target)
    if target.exists():
        file(target)
    if os.name == 'nt':
        import ctypes
        from ctypes import wintypes
        move = ctypes.WinDLL('kernel32', use_last_error=True).MoveFileExW
        move.argtypes = [wintypes.LPCWSTR, wintypes.LPCWSTR, wintypes.DWORD]
        move.restype = wintypes.BOOL
        require(bool(move(str(source), str(target), 0x1 | 0x8)), 'CONTROL_REPLACE_FAILED')
    else:
        os.replace(source, target)
        sync_directory(target.parent)


def write_pointer(root, value, fault):
    staged = root / ('pointer-' + value['recordSha256'] + '.pending')
    if staged.exists():
        require(read(staged) == value, 'CONTROL_PARTIAL_POINTER')
    else:
        append(staged, canonical(value))
    fault('pointer_staged')
    replace(staged, root / 'current.json')
    fault('pointer_committed')


def lock_byte(stream, acquire):
    stream.seek(0)
    if os.name == 'nt':
        import msvcrt
        msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK if acquire else msvcrt.LK_UNLCK, 1)
    else:
        import fcntl
        # Java FileChannel uses POSIX record locks.  Use lockf here as well;
        # flock and fcntl locks do not exclude one another on Linux.
        fcntl.lockf(stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB if acquire else fcntl.LOCK_UN, 1, 0, os.SEEK_SET)


@contextmanager
def locked(root):
    require(_PROCESS_LOCK.acquire(blocking=False), 'CONTROL_COORDINATOR_BUSY')
    try:
        with locked_file(root):
            yield
    finally:
        _PROCESS_LOCK.release()


@contextmanager
def locked_file(root):
    with file(root / 'coordinator.lock').open('r+b', buffering=0) as stream:
        require(os.fstat(stream.fileno()).st_size == 1, 'CONTROL_LOCK_INVALID')
        try:
            lock_byte(stream, True)
        except OSError:
            raise OpsError('CONTROL_COORDINATOR_BUSY') from None
        try:
            yield
        finally:
            lock_byte(stream, False)
