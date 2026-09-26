package com.acrylic.outbound.exports;

import com.sun.jna.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.win32.StdCallLibrary;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** A non-inheritable kernel job kills every assigned descendant when its owner dies. */
final class ExportWindowsJob implements AutoCloseable {
    interface Api extends StdCallLibrary {
        Api INSTANCE = Native.load("kernel32", Api.class);
        WinNT.HANDLE CreateJobObjectW(Pointer security, WString name);
        boolean SetInformationJobObject(WinNT.HANDLE job, int type, Pointer info, int size);
        boolean QueryInformationJobObject(WinNT.HANDLE job, int type, Pointer info, int size, Pointer returned);
        boolean AssignProcessToJobObject(WinNT.HANDLE job, WinNT.HANDLE process);
        boolean TerminateJobObject(WinNT.HANDLE job, int code);
    }

    private final WinNT.HANDLE handle;
    private boolean closed;

    ExportWindowsJob() {
        require(Native.POINTER_SIZE == 8, "EXPORT_JOB_ARCHITECTURE_UNSUPPORTED");
        handle = Api.INSTANCE.CreateJobObjectW(null, null);
        require(handle != null && !WinBase.INVALID_HANDLE_VALUE.equals(handle), "EXPORT_JOB_CREATE_FAILED");
        // Windows x64 JOBOBJECT_EXTENDED_LIMIT_INFORMATION: size144, BasicLimitInformation.LimitFlags offset16.
        try (var info = new Memory(144)) {
            info.clear(); info.setInt(16, 0x2000); // JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE; no breakaway permitted.
            if (!Api.INSTANCE.SetInformationJobObject(handle, 9, info, 144)) {
                close(); throw new IllegalStateException("EXPORT_JOB_CONFIGURE_FAILED");
            }
        }
    }

    synchronized void assign(Process process) {
        require(!closed, "EXPORT_JOB_CLOSED");
        int pid = Math.toIntExact(process.pid());
        var child = Kernel32.INSTANCE.OpenProcess(0x0100 | 0x0001, false, pid); // SET_QUOTA | TERMINATE
        require(child != null && !WinBase.INVALID_HANDLE_VALUE.equals(child), "EXPORT_JOB_PROCESS_UNAVAILABLE");
        try { require(Api.INSTANCE.AssignProcessToJobObject(handle, child), "EXPORT_JOB_ASSIGN_FAILED"); }
        finally { Kernel32.INSTANCE.CloseHandle(child); }
    }

    synchronized void stop(Duration timeout) {
        if (closed) return;
        require(Api.INSTANCE.TerminateJobObject(handle, 1), "EXPORT_JOB_TERMINATE_FAILED");
        boolean interrupted = Thread.interrupted();
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (active() != 0) {
                require(System.nanoTime() < deadline, "EXPORT_PROCESS_DRAIN_INCOMPLETE");
                try { TimeUnit.MILLISECONDS.sleep(20); }
                catch (InterruptedException ignored) { interrupted = true; }
            }
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }

    private int active() {
        // JOBOBJECT_BASIC_ACCOUNTING_INFORMATION: size48, ActiveProcesses offset40 on Windows x64.
        try (var info = new Memory(48)) {
            require(Api.INSTANCE.QueryInformationJobObject(handle, 1, info, 48, null), "EXPORT_JOB_QUERY_FAILED");
            return info.getInt(40);
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        require(Kernel32.INSTANCE.CloseHandle(handle), "EXPORT_JOB_CLOSE_FAILED");
        closed = true;
    }

    private static void require(boolean value, String code) {
        if (!value) throw new IllegalStateException(code);
    }
}
