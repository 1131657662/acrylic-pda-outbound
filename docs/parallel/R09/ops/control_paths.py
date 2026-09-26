"""Reject restore-set overlap and path aliases before control-store I/O."""
from dataclasses import dataclass
from pathlib import Path
import os
import stat
from control_format import uuid
from safety import require


def checked(path):
    path = Path(path)
    require(path.is_absolute() and '..' not in path.parts, 'CONTROL_ABSOLUTE_PATH_REQUIRED')
    require(not str(path).startswith(('\\\\', '//')), 'CONTROL_REMOTE_PATH_UNVERIFIED')
    if os.name == 'nt':
        local_windows_path(path)
    for component in (path, *path.parents):
        require(not component.is_symlink() and not component.is_junction(), 'CONTROL_PATH_LINK')
        if component.exists():
            attributes = getattr(component.lstat(), 'st_file_attributes', 0)
            require(not attributes & 0x400, 'CONTROL_REPARSE_POINT')
    return path.resolve()


def local_windows_path(path):
    import ctypes
    from ctypes import wintypes
    drive_type = ctypes.WinDLL('kernel32', use_last_error=True).GetDriveTypeW
    drive_type.argtypes = [wintypes.LPCWSTR]
    drive_type.restype = wintypes.UINT
    require(drive_type(path.anchor) == 3, 'CONTROL_LOCAL_FIXED_DRIVE_REQUIRED')
    reserved = {'CON', 'PRN', 'AUX', 'NUL', *(f'COM{i}' for i in range(1, 10)), *(f'LPT{i}' for i in range(1, 10))}
    for part in path.parts[1:]:
        require(part.rstrip(' .') == part and ':' not in part and part.split('.')[0].upper() not in reserved,
                'CONTROL_PATH_ALIAS')


def file(path):
    path = checked(path)
    require(path.is_file(), 'CONTROL_FILE_MISSING')
    details = path.stat()
    require(stat.S_ISREG(details.st_mode) and details.st_nlink == 1, 'CONTROL_FILE_ALIAS')
    return path


@dataclass(frozen=True)
class ControlLocation:
    root: Path
    installation_id: str
    restore_roots: tuple

    def validated(self):
        uuid(self.installation_id)
        root = checked(self.root)
        require(bool(self.restore_roots), 'CONTROL_RESTORE_SET_REQUIRED')
        roots = tuple(sorted({str(checked(item)) for item in self.restore_roots}))
        for other in (Path(item) for item in roots):
            require(not root.is_relative_to(other) and not other.is_relative_to(root),
                    'CONTROL_INSIDE_RESTORE_SET')
        return ControlLocation(root, self.installation_id, roots)

    def identity(self):
        return {'formatVersion': 1, 'installationId': self.installation_id,
                'restoreRoots': list(self.restore_roots)}
