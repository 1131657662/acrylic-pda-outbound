package com.acrylic.outbound.inventory;

import java.nio.file.Files;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

/** First FORMAL fault writers wait on the actual guard and return one immutable original response. */
final class HandoverFormalFaultConcurrentChecks {
    static byte[] execute(HandoverFormalFaultSource f) throws Exception {
        var second = new HandoverApprovalHttp(f.workflow.web.username(), f.source.directory);
        try (var connection = f.source.jdbc.getDataSource().getConnection(); var workers = Executors.newFixedThreadPool(2)) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.executeQuery("SELECT id FROM user_management_guard WHERE id=1 FOR UPDATE").close();
            }
            var one = workers.submit(() -> f.workflow.web.command(f.command, 200, null));
            var two = workers.submit(() -> second.command(f.command, 200, null));
            int waits = waitForWriters(f); connection.commit();
            byte[] receipt = one.get(30, TimeUnit.SECONDS);
            assertThat(two.get(30, TimeUnit.SECONDS)).isEqualTo(receipt);
            requireUnique(f);
            Files.writeString(f.source.directory.resolve("formal-concurrent.marker"),
                "Two first real HTTP writers waited on guard: " + waits + "; one FORMAL fence/receipt/outbox/audit; identical original response.\n");
            return receipt;
        }
    }

    private static int waitForWriters(HandoverFormalFaultSource f) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20); int waits = 0;
        while (waits < 2 && System.nanoTime() < deadline) {
            waits = f.source.jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits", Integer.class);
            if (waits < 2) Thread.sleep(50);
        }
        assertThat(waits).isGreaterThanOrEqualTo(2);
        return waits;
    }

    private static void requireUnique(HandoverFormalFaultSource f) {
        var jdbc = f.source.jdbc; String id = f.source.kase.toString();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM handover_formal_fault_fence WHERE case_id=UUID_TO_BIN(?)", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM handover_command_receipt WHERE case_id=UUID_TO_BIN(?) AND command_type='FAULT_FENCE'", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_event e JOIN handover_command_receipt r ON r.id=e.entity_id "
            + "WHERE r.case_id=UUID_TO_BIN(?) AND e.event_type='HANDOVER_FAULT_FENCE'", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_sync_outbox WHERE order_id=UUID_TO_BIN(?) AND event_type='HANDOVER_FAULT_FENCED'", Long.class,
            f.source.r.f.body.at("/source/sourceOrderId").asText())).isEqualTo(1);
    }
}
