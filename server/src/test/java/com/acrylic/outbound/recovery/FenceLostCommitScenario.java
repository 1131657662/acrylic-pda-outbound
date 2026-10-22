package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/** Test-only transport loss after the real MySQL driver returns from commit. */
final class FenceLostCommitScenario {
    static void run(FenceTestSupply supply, Path directory, String table) throws Exception {
        var authority = supply.authority();
        var gate = RecoveryEffectGate.enabled(authority);
        var operation = authority.registerOperation();
        Files.writeString(directory.resolve("operation.txt"), operation.toString(), StandardOpenOption.CREATE_NEW);
        try (var jdbc = new FenceRealJdbc(gate, table, true)) {
            jdbc.connection.setAutoCommit(false);
            jdbc.connection.createStatement().executeUpdate(jdbc.insert(2));
            status(directory, "PREPARED|uncommitted=2|delegateInsert=" + jdbc.calls.count("Statement.executeUpdate"));
            waitFor(directory.resolve("go"));
            jdbc.connection.commit(); // LostCommitDataSource commits first; product UNKNOWN must halt this JVM.
            Files.writeString(directory.resolve("commit-returned.marker"), "unexpected success", StandardOpenOption.CREATE_NEW);
            throw new AssertionError("Lost commit acknowledgement did not halt the owned JVM");
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
