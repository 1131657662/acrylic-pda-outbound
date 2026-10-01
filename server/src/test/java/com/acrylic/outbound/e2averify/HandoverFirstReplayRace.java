package com.acrylic.outbound.e2averify;

import java.util.List;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Holds the case until both first deliveries are observed waiting in distinct MySQL transactions. */
final class HandoverFirstReplayRace {
    private final JdbcTemplate jdbc;
    private final java.nio.file.Path attempt;
    private final HandoverRollbackFixture fixture;

    HandoverFirstReplayRace(HandoverRollbackFixture fixture, java.nio.file.Path attempt) {
        this.fixture = fixture; this.jdbc = fixture.jdbc; this.attempt = attempt;
    }

    List<Outcome> execute(Callable<byte[]> call) throws Exception {
        try (var blocker = jdbc.getDataSource().getConnection(); var pool = Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            try {
                lockCase(blocker);
                var ready = new CountDownLatch(2);
                var start = new CountDownLatch(1);
                var first = pool.submit(() -> invoke(call, ready, start));
                var second = pool.submit(() -> invoke(call, ready, start));
                assertThat(ready.await(10, TimeUnit.SECONDS)).as("both worker threads ready").isTrue();
                start.countDown();
                awaitTwoDatabaseWaiters(List.of(first, second));
                blocker.commit();
                return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            } finally {
                blocker.rollback();
                pool.shutdownNow();
            }
        }
    }

    private void lockCase(java.sql.Connection connection) throws Exception {
        try (var statement = connection.prepareStatement("SELECT row_version FROM handover_case WHERE id=? FOR UPDATE")) {
            statement.setBytes(1, com.acrylic.outbound.common.Ids.bytes(fixture.caseId));
            try (var rows = statement.executeQuery()) { assertThat(rows.next()).isTrue(); }
        }
    }

    private void awaitTwoDatabaseWaiters(List<Future<Outcome>> workers) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            var waits = jdbc.queryForList("SELECT ENGINE_TRANSACTION_ID,THREAD_ID,LOCK_TYPE,LOCK_MODE,LOCK_STATUS,LOCK_DATA "
                + "FROM performance_schema.data_locks WHERE OBJECT_SCHEMA=DATABASE() "
                + "AND OBJECT_NAME='handover_case' AND LOCK_STATUS='WAITING' ORDER BY THREAD_ID");
            if (waits.stream().map(row -> row.get("THREAD_ID")).distinct().count() == 2) {
                assertThat(waits.stream().map(row -> row.get("ENGINE_TRANSACTION_ID")).distinct().count()).isEqualTo(2);
                var json = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(waits);
                java.nio.file.Files.writeString(attempt.resolve("first-replay-lock-waiters.json"), json.toPrettyString());
                return;
            }
            assertThat(workers).as("neither first delivery finishes while its case is locked")
                .allMatch(worker -> !worker.isDone());
            Thread.sleep(50);
        }
        throw new AssertionError("Two real case lock waiters not observed; cannot claim overlapping first delivery");
    }

    private static Outcome invoke(Callable<byte[]> call, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start barrier timed out");
        try { return new Outcome("OK", call.call()); }
        catch (com.acrylic.outbound.common.BusinessException failure) { return new Outcome(failure.code(), null); }
    }

    record Outcome(String code, byte[] response) { }
}
