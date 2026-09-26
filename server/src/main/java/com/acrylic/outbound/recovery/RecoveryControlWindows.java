package com.acrylic.outbound.recovery;

import com.sun.jna.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.win32.StdCallLibrary;
import java.nio.file.Path;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Native metadata needed to reject mapped drives, reparse points and file aliases. */
final class RecoveryControlWindows {
    interface FileApi extends StdCallLibrary {
        FileApi INSTANCE = Native.load("kernel32", FileApi.class);
        boolean GetFileInformationByHandle(WinNT.HANDLE file, Pointer information);
    }
    private RecoveryControlWindows() { }

    static void local(Path path) {
        require(Kernel32.INSTANCE.GetDriveType(path.getRoot().toString()) == 3, "CONTROL_LOCAL_FIXED_DRIVE_REQUIRED");
    }

    static void noReparse(Path path) {
        int attributes = Kernel32.INSTANCE.GetFileAttributes(path.toString());
        require(attributes != -1 && (attributes & 0x400) == 0, "CONTROL_REPARSE_POINT");
    }

    static void singleLink(Path path) {
        fileIdentity(path);
    }

    static String fileIdentity(Path path) {
        var handle = Kernel32.INSTANCE.CreateFile(path.toString(), 0, 7, null, 3, 0x00200000, null);
        require(!WinBase.INVALID_HANDLE_VALUE.equals(handle), "CONTROL_FILE_UNAVAILABLE");
        try (var info = new Memory(52)) {
            require(FileApi.INSTANCE.GetFileInformationByHandle(handle, info), "CONTROL_FILE_UNAVAILABLE");
            require(info.getInt(40) == 1, "CONTROL_FILE_ALIAS");
            long index = (Integer.toUnsignedLong(info.getInt(44)) << 32) | Integer.toUnsignedLong(info.getInt(48));
            return "windows:" + Integer.toUnsignedString(info.getInt(28)) + ":" + Long.toUnsignedString(index);
        } finally { Kernel32.INSTANCE.CloseHandle(handle); }
    }
}
