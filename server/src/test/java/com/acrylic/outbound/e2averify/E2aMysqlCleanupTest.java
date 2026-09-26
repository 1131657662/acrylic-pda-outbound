package com.acrylic.outbound.e2averify;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class E2aMysqlCleanupTest {
    @Test
    void refusesToStopExitedUnknownProcess() {
        Assumptions.assumeTrue(E2aMysqlLauncher.isPortFree());
        Process unknown = new Process() {
            @Override public boolean isAlive() { return false; }
            @Override public void destroy() { throw new AssertionError("Unknown PID must not be killed"); }
            @Override public boolean waitFor(long timeout, TimeUnit unit) { return true; }
            @Override public int waitFor() { return 0; }
            @Override public int exitValue() { return 0; }
            @Override public java.io.OutputStream getOutputStream() { return java.io.OutputStream.nullOutputStream(); }
            @Override public java.io.InputStream getInputStream() { return java.io.InputStream.nullInputStream(); }
            @Override public java.io.InputStream getErrorStream() { return java.io.InputStream.nullInputStream(); }
        };
        Path workspace = E2aMigrationRunner.workspace();

        assertThatThrownBy(() -> E2aMysqlCleanup.stop(unknown, workspace,
            workspace.resolve(E2aMigrationEnvironment.DATA_DIR), "a".repeat(64)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("without authenticated shutdown");
    }
}
