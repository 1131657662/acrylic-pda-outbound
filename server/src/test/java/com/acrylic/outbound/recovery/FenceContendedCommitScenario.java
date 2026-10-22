package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

/** Test-only handshake after the actual driver commits, before synthetic acknowledgement loss. */
final class FenceContendedCommitScenario {
    static void run(FenceTestSupply supply, Path directory, String table) throws Exception {
        var authority = supply.authority();
        var gate = RecoveryEffectGate.enabled(authority);
        var operation = authority.registerOperation();
        Files.writeString(directory.resolve("operation.txt"), operation.toString(), StandardOpenOption.CREATE_NEW);
        try (var jdbc = new FenceRealJdbc(gate, table, () -> afterCommit(directory))) {
            jdbc.connection.setAutoCommit(false);
            jdbc.connection.createStatement().executeUpdate(jdbc.insert(2));
            status(directory, "PREPARED|uncommitted=2|delegateInsert=" + jdbc.calls.count("Statement.executeUpdate"));
            waitFor(directory.resolve("go"));
            jdbc.connection.commit();
            Files.writeString(directory.resolve("commit-returned.marker"), "unexpected success", StandardOpenOption.CREATE_NEW);
            throw new AssertionError("Contended lost acknowledgement did not halt the owned JVM");
        }
    }

    private static void afterCommit(Path directory) throws SQLException {
        try {
            status(directory, "DRIVER_COMMITTED|awaiting=lose-ack");
            waitFor(directory.resolve("lose-ack"));
            status(directory, "ACK_LOSS_RELEASED");
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new SQLException("Test post-commit handshake failed", "HY000", error);
        }
    }

    private static void waitFor(Path command) throws Exception {
        long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (!Files.isRegularFile(command)) {
            if (System.nanoTime() > limit) throw new AssertionError("Missing command: " + command);
            Thread.sleep(20);
        }
    }

    private static void status(Path directory, String text) throws Exception {
        Files.writeString(directory.resolve("status.txt"), text + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
