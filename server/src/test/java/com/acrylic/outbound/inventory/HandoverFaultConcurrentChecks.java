package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Two live HTTP retries wait at the shared write guard, then consume one durable original fence. */
final class HandoverFaultConcurrentChecks {
    static byte[] execute(HandoverTemporaryFaultFenceChecks f, ObjectNode command) throws Exception {
        var secondReviewer = new HandoverApprovalHttp(f.faultReviewer.username(), f.directory);
        try (var connection = f.source.jdbc.getDataSource().getConnection(); var workers = Executors.newFixedThreadPool(2)) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.executeQuery("SELECT id FROM user_management_guard WHERE id=1 FOR UPDATE").close();
            }
            var first = workers.submit(() -> f.faultReviewer.command(command, 200, null));
            var second = workers.submit(() -> secondReviewer.command(command, 200, null));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20); int waits = 0;
            while (waits < 2 && System.nanoTime() < deadline) {
                waits = f.source.jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits", Integer.class);
                if (waits < 2) Thread.sleep(50);
            }
            assertThat(waits).isGreaterThanOrEqualTo(2); connection.commit();
            byte[] receipt = first.get(30, TimeUnit.SECONDS);
            assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo(receipt);
            java.nio.file.Files.writeString(f.directory.resolve("concurrent.marker"),
                "Two actual HTTP callers blocked on shared guard; one fence, same original response bytes.\n");
            return receipt;
        }
    }
}
