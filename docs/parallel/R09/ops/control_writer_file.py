"""Stable local lock identity, using metadata only (never another POSIX descriptor)."""
import os
from control_paths import file
from safety import require


def identity(path):
    path = file(path)
    if os.name != 'nt':
        details = path.stat()
        return f'posix:{details.st_dev}:{details.st_ino}'
    import ctypes
    from ctypes import wintypes
    api = ctypes.WinDLL('kernel32', use_last_error=True)
    api.CreateFileW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD, ctypes.c_void_p,
                               wintypes.DWORD, wintypes.DWORD, wintypes.HANDLE]
    api.CreateFileW.restype = wintypes.HANDLE
    api.GetFileInformationByHandle.argtypes = [wintypes.HANDLE, ctypes.c_void_p]
    api.GetFileInformationByHandle.restype = wintypes.BOOL
    api.CloseHandle.argtypes = [wintypes.HANDLE]
    api.CloseHandle.restype = wintypes.BOOL
    handle = api.CreateFileW(str(path), 0, 7, None, 3, 0x00200000, None)
    require(handle != ctypes.c_void_p(-1).value, 'CONTROL_FILE_UNAVAILABLE')
    try:
        data = (wintypes.DWORD * 13)()
        require(bool(api.GetFileInformationByHandle(handle, ctypes.byref(data))), 'CONTROL_FILE_UNAVAILABLE')
        require(data[10] == 1, 'CONTROL_FILE_ALIAS')
        return f'windows:{data[7]}:{(data[11] << 32) | data[12]}'
    finally:
        api.CloseHandle(handle)
