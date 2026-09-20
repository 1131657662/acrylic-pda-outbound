"""Local path, process and redacted failure boundaries for R09 tools."""
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
from contextlib import contextmanager


class OpsError(Exception):
    """Only fixed, non-sensitive failure codes may cross the CLI boundary."""


def require(condition, code):
    if not condition:
        raise OpsError(code)


def run(command, **kwargs):
    result = subprocess.run(command, capture_output=True, timeout=180, **kwargs)
    require(result.returncode == 0, 'CHILD_PROCESS_FAILED')
    return result.stdout


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def digest(value):
    return hashlib.sha256(value).hexdigest()


def write_json(path, value):
    with Path(path).open('x', encoding='utf-8') as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
        stream.flush()
        os.fsync(stream.fileno())


def read_json(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def regular(path):
    path = Path(path)
    require(not path.is_symlink() and not path.is_junction(), 'LINK_REFUSED')
    require(path.is_file(), 'REGULAR_FILE_REQUIRED')
    return path


def child(root, relative):
    name = PurePosixPath(relative)
    require(not name.is_absolute() and bool(name.parts), 'UNSAFE_PATH')
    require(not any(p in ('..', '.') for p in name.parts), 'UNSAFE_PATH')
    require('\\' not in relative and ':' not in relative, 'UNSAFE_PATH')
    reserved = {'CON', 'PRN', 'AUX', 'NUL', *(f'COM{i}' for i in range(1, 10)), *(f'LPT{i}' for i in range(1, 10))}
    require(all(p.rstrip(' .') == p and p.split('.')[0].upper() not in reserved for p in name.parts), 'UNSAFE_PATH')
    root = Path(root).resolve()
    target = root.joinpath(*name.parts)
    require(target.resolve().is_relative_to(root), 'PATH_ESCAPES_ROOT')
    for part in [target, *target.parents]:
        if part == root:
            break
        require(not part.is_symlink() and not part.is_junction(), 'LINK_REFUSED')
    return target


def inventory(root):
    root = Path(root)
    require(root.is_dir() and not root.is_symlink() and not root.is_junction(), 'BAD_TREE_ROOT')
    result = {}
    for path in sorted(root.rglob('*')):
        require(not path.is_symlink() and not path.is_junction(), 'LINK_REFUSED')
        if path.is_dir():
            continue
        regular(path)
        result[path.relative_to(root).as_posix()] = {'bytes': path.stat().st_size, 'sha256': sha(path)}
    return result


def secure_directory(path):
    path.mkdir(mode=0o700)
    if os.name == 'nt':
        import csv
        identity = run(['whoami', '/user', '/fo', 'csv', '/nh']).decode().strip()
        sid = next(csv.reader([identity]))[1]
        run(['icacls', str(path), '/inheritance:r', '/grant:r',
             f'*{sid}:(OI)(CI)F', '*S-1-5-18:(OI)(CI)F'])
    else:
        path.chmod(0o700)


@contextmanager
def exclusive(root):
    lock = Path(root) / '.r09-operation.lock'
    try:
        stream = lock.open('x', encoding='ascii')
    except FileExistsError:
        raise OpsError('R09_OPERATION_LOCKED') from None
    try:
        with stream:
            stream.write(str(os.getpid()))
        yield
    finally:
        regular(lock)
        lock.unlink()  # Only this invocation's exact lock; never remove an existing stale lock.
