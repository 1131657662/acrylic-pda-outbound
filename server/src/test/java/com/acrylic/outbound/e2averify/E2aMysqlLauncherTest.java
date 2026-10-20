package com.acrylic.outbound.e2averify;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class E2aMysqlLauncherTest {
    @Test
    @EnabledOnOs(OS.WINDOWS)
    void restrictsPrivateDirectoryWithoutInheritedPowerShellModules(@TempDir Path directory) throws Exception {
        assertThat(E2aMysqlLauncher.powershell("$null").environment().keySet())
            .noneMatch(name -> name.equalsIgnoreCase("PSModulePath"));
        E2aMysqlLauncher.restrictToCurrentUser(directory);
        E2aMysqlLauncher.restrictToCurrentUser(directory);

        assertThat(Files.getFileAttributeView(directory, AclFileAttributeView.class).getAcl()).hasSize(1);
    }

    @Test
    void confirmsExitAfterRepeatedInterruptions() {
        var process = new InterruptedProcess(true);
        var failure = new InterruptedException("initial wait interrupted");

        try {
            E2aMysqlLauncher.cleanupInitialization(process, failure, TimeUnit.MILLISECONDS.toNanos(20));

            assertThat(process.isAlive()).isFalse();
            assertThat(process.forced).isTrue();
            assertThat(failure.getSuppressed()).hasSize(2).allMatch(InterruptedException.class::isInstance);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void reportsProcessThatRemainsAfterForcedCleanup() {
        var process = new InterruptedProcess(false);
        var failure = new IllegalStateException("initial wait failed");

        E2aMysqlLauncher.cleanupInitialization(process, failure, TimeUnit.MILLISECONDS.toNanos(20));

        assertThat(process.isAlive()).isTrue();
        assertThat(failure.getSuppressed()).anyMatch(error -> error.getMessage().contains("remained after forced cleanup"));
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    private static final class InterruptedProcess extends Process {
        private final boolean exitsAfterForce;
        private boolean alive = true;
        private boolean forced;
        private int waits;

        private InterruptedProcess(boolean exitsAfterForce) { this.exitsAfterForce = exitsAfterForce; }

        @Override public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            if (++waits <= 2 && exitsAfterForce) throw new InterruptedException("cleanup interrupted");
            if (forced && exitsAfterForce) { alive = false; return true; }
            TimeUnit.NANOSECONDS.sleep(unit.toNanos(timeout));
            return false;
        }

        @Override public int waitFor() { throw new UnsupportedOperationException(); }
        @Override public int exitValue() { if (alive) throw new IllegalThreadStateException(); return 0; }
        @Override public void destroy() { }
        @Override public Process destroyForcibly() { forced = true; return this; }
        @Override public boolean isAlive() { return alive; }
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
    }
}
