package com.acrylic.outbound.e2averify;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

final class E2aMysqlProcessIdentity {
    private E2aMysqlProcessIdentity() { }

    record Listener(long processId, long parentProcessId, String executable, String commandLine) { }

    static long requireOwnedListener(Process launched, Path data) throws IOException, InterruptedException {
        Listener listener = readListener();
        requireExpected(listener, launched.pid(), data);
        return listener.processId();
    }

    static void requireExpected(Listener listener, long launchedPid, Path data) {
        String executable = E2aMysqlLauncher.MYSQLD.toString().replace('\\', '/');
        String expectedData = data.toAbsolutePath().normalize().toString().replace('\\', '/');
        String expectedInit = expectedInitFile();
        String command = listener.commandLine().replace('\\', '/');
        boolean related = listener.processId() == launchedPid || listener.parentProcessId() == launchedPid;
        if (!related || !executable.equalsIgnoreCase(listener.executable().replace('\\', '/'))
                || !command.contains("--datadir=" + expectedData + " --port=" + E2aMigrationEnvironment.PORT + " ")
                || !command.contains("--bind-address=127.0.0.1")
                || !command.contains("--skip-name-resolve")
                || !command.contains("--skip-grant-tables=false")
                || !command.endsWith("--init-file=" + expectedInit)) {
            throw new IllegalStateException("19329 listener is not the exact launched MySQL process or child");
        }
    }

    static void verifyStartedProcess(Process process, Path data) throws IOException, InterruptedException {
        if (!process.isAlive()) throw new IllegalStateException("MySQL startup process exited before identity verification");
        String command = "$p=Get-CimInstance Win32_Process -Filter 'ProcessId=" + process.pid() + "'; "
            + "if ($null -eq $p) { throw 'Missing startup process' }; "
            + "[Console]::Out.WriteLine($p.ExecutablePath); [Console]::Out.WriteLine($p.CommandLine)";
        Process probe = E2aMysqlLauncher.powershell(command).redirectErrorStream(true).start();
        if (!probe.waitFor(30, TimeUnit.SECONDS) || probe.exitValue() != 0) {
            probe.destroyForcibly();
            throw new IllegalStateException("MySQL startup PID probe failed");
        }
        String[] output = new String(probe.getInputStream().readAllBytes()).trim().split("\\R", 2);
        String expected = data.toAbsolutePath().normalize().toString().replace('\\', '/');
        if (output.length != 2 || !E2aMysqlLauncher.MYSQLD.toString().replace('\\', '/').equalsIgnoreCase(output[0].replace('\\', '/'))
                || !output[1].replace('\\', '/').contains("--datadir=" + expected + " --port=" + E2aMigrationEnvironment.PORT + " ")
                || !output[1].contains("--bind-address=127.0.0.1")
                || !output[1].replace('\\', '/').endsWith("--init-file=" + expectedInitFile())) {
            throw new IllegalStateException("MySQL startup PID/datadir/port mismatch");
        }
    }

    private static String expectedInitFile() {
        return E2aMigrationRunner.workspace().resolve(E2aMigrationEnvironment.PRIVATE_DIR)
            .resolve("bootstrap-account.sql").toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    private static Listener readListener() throws IOException, InterruptedException {
        String script = "$listeners=@(Get-NetTCPConnection -LocalAddress '127.0.0.1' -LocalPort "
            + E2aMigrationEnvironment.PORT + " -State Listen -ErrorAction Stop); "
            + "if ($listeners.Count -ne 1) { throw 'Unexpected listener count' }; "
            + "$owner=Get-CimInstance Win32_Process -Filter ('ProcessId=' + $listeners[0].OwningProcess); "
            + "if ($null -eq $owner) { throw 'Missing listener process' }; "
            + "[Console]::Out.WriteLine($owner.ProcessId); [Console]::Out.WriteLine($owner.ParentProcessId); "
            + "[Console]::Out.WriteLine($owner.ExecutablePath); [Console]::Out.WriteLine($owner.CommandLine)";
        Process probe = E2aMysqlLauncher.powershell(script).redirectErrorStream(true).start();
        if (!probe.waitFor(30, TimeUnit.SECONDS) || probe.exitValue() != 0) {
            probe.destroyForcibly();
            throw new IllegalStateException("19329 listener identity probe failed");
        }
        String[] lines = new String(probe.getInputStream().readAllBytes()).trim().split("\\R", 4);
        if (lines.length != 4) throw new IllegalStateException("Incomplete 19329 listener identity");
        return new Listener(Long.parseLong(lines[0]), Long.parseLong(lines[1]), lines[2], lines[3]);
    }
}
