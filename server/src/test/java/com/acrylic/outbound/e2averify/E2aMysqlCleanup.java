package com.acrylic.outbound.e2averify;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

final class E2aMysqlCleanup {
    private E2aMysqlCleanup() { }

    static void stop(Process server, Path workspace, Path data, String password) throws Exception {
        if (E2aMysqlLauncher.isPortFree()) {
            if (!server.isAlive()) throw new IllegalStateException("MySQL exited without authenticated shutdown");
            IllegalStateException failure = new IllegalStateException("MySQL listener disappeared before authenticated shutdown");
            fallback(server, data, failure);
            throw failure;
        }
        try {
            E2aMysqlProcessIdentity.requireOwnedListener(server, data);
            E2aMysqlLauncher.shutdown(server, workspace, E2aMigrationEnvironment.USER, password);
        } catch (Exception failure) {
            fallback(server, data, failure);
            throw new IllegalStateException("Authenticated shutdown failed; fallback is not a clean stop", failure);
        }
    }

    private static void fallback(Process server, Path data, Exception failure) {
        try {
            if (!E2aMysqlLauncher.isPortFree()) stopOwnedListener(server, data);
            if (server.isAlive()) {
                E2aMysqlProcessIdentity.verifyStartedProcess(server, data);
                server.destroy();
                if (!server.waitFor(10, TimeUnit.SECONDS)) {
                    E2aMysqlProcessIdentity.verifyStartedProcess(server, data);
                    server.destroyForcibly();
                    if (!server.waitFor(10, TimeUnit.SECONDS)) throw new IllegalStateException("Startup process remains");
                }
            }
            if (!E2aMysqlLauncher.isPortFree()) throw new IllegalStateException("19329 listener remains");
        } catch (Exception cleanup) {
            failure.addSuppressed(cleanup);
            if (cleanup instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    private static void stopOwnedListener(Process server, Path data) throws Exception {
        long pid = E2aMysqlProcessIdentity.requireOwnedListener(server, data);
        ProcessHandle listener = ProcessHandle.of(pid).orElseThrow();
        if (!listener.isAlive()) throw new IllegalStateException("Listener exited during ownership probe");
        listener.destroy();
        if (!listener.onExit().completeOnTimeout(listener, 10, TimeUnit.SECONDS).get().isAlive()) return;
        if (E2aMysqlProcessIdentity.requireOwnedListener(server, data) != pid) {
            throw new IllegalStateException("Listener changed during fallback stop");
        }
        listener.destroyForcibly();
        if (listener.onExit().completeOnTimeout(listener, 10, TimeUnit.SECONDS).get().isAlive()) {
            throw new IllegalStateException("Owned listener remains after fallback stop");
        }
    }
}
