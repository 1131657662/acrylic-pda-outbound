package com.acrylic.outbound.recovery;

import java.sql.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryEffectAdversarialCleanupIT {
    @BeforeAll static void environment() throws Exception { EffectRealEnvironment.requireEnvironment(); }
    static Stream<Arguments> actions() {
        return IntStream.rangeClosed(1, 3).boxed().flatMap(n -> Stream.of("query", "execute", "update",
            "largeUpdate", "batch", "largeBatch", "insertRow", "updateRow", "deleteRow", "updateString")
                .map(action -> Arguments.of(n, action)));
    }

    @ParameterizedTest @MethodSource("actions")
    void anyExecutionOrRowMutationInvalidatesVerifiedEndpoint(int repeat, String action) throws Exception {
        try (var f = new EffectAdversarialFixture(true, false); var s = f.connection.createStatement()) {
            f.connection.setAutoCommit(false);
            try (var r = f.rows()) {
                if (action.equals("insertRow")) { r.moveToInsertRow(); r.updateInt(1, 2); }
                r.updateString(3, "changed");
                s.addBatch(f.writeSql());
                f.connection.commit();
                change(f, s, r, action);
                int before = f.calls.count("Connection.setAutoCommit");
                f.gate.fence();
                assertThatThrownBy(() -> f.connection.setAutoCommit(true)).hasMessage("RECOVERY_LEASE_FENCED");
                assertThat(f.calls.count("Connection.setAutoCommit")).isEqualTo(before);
                assertThat(f.observed()).isEqualTo("1:62:original;");
                f.connection.rollback(); f.connection.setAutoCommit(true);
                f.record("cleanup-invalidated-" + action + "|repeat=" + repeat + "|resetDelta=0|rollbackResetAllowed=true");
            }
        }
    }

    private static void change(EffectAdversarialFixture f, Statement s, ResultSet r, String action) throws Exception {
        switch (action) {
            case "query" -> s.executeQuery("SELECT 1").close();
            case "execute" -> s.execute(f.writeSql());
            case "update" -> s.executeUpdate(f.writeSql());
            case "largeUpdate" -> s.executeLargeUpdate(f.writeSql());
            case "batch" -> s.executeBatch();
            case "largeBatch" -> s.executeLargeBatch();
            case "insertRow" -> r.insertRow();
            case "updateRow" -> r.updateRow();
            case "deleteRow" -> r.deleteRow();
            case "updateString" -> r.updateString(3, "again");
            default -> throw new AssertionError(action);
        }
    }

    @RepeatedTest(3) void closedConnectionAndNewBorrowShareTheFence() throws Exception {
        try (var f = new EffectAdversarialFixture(true, false)) {
            f.connection.close(); f.gate.fence();
            try (var next = f.adapter.getConnection()) {
                assertThat(next).isNotSameAs(f.connection);
                assertThatThrownBy(() -> next.createStatement().executeUpdate(f.writeSql()))
                    .hasMessage("RECOVERY_LEASE_FENCED");
                assertThat(f.calls.count("Statement.executeUpdate")).isZero();
                assertThat(f.observed()).isEqualTo("1:62:original;");
                f.record("close-reborrow|executeDelta=0");
            }
        }
    }

    @RepeatedTest(3) void twoThreadsSerializeAndSecondSeesFence() throws Exception {
        try (var f = new EffectAdversarialFixture(true, false)) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var block = new AtomicBoolean(false);
            var pool = Executors.newFixedThreadPool(2);
            try (var c = f.adapter.admit(() -> {
                if (block.compareAndSet(true, false)) {
                    entered.countDown();
                    try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("probe timeout"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                }
            })) {
                runSerialized(f, c, pool, block, entered, release);
            } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
        }
    }

    private static void runSerialized(EffectAdversarialFixture f, Connection c, ExecutorService pool,
            AtomicBoolean block, CountDownLatch entered, CountDownLatch release) throws Exception {
        var s = c.createStatement(); block.set(true);
        var first = pool.submit(() -> { c.getAutoCommit(); return true; });
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        var started = new CountDownLatch(1);
        var second = pool.submit(() -> { started.countDown(); return catchThrowable(() -> s.executeUpdate(f.writeSql())); });
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> second.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        f.gate.fence(); release.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(second.get(10, TimeUnit.SECONDS)).hasMessage("RECOVERY_LEASE_FENCED");
        assertThat(f.calls.count("Statement.executeUpdate")).isZero();
        assertThat(f.observed()).isEqualTo("1:62:original;");
        f.record("two-threads|monitorSerialized=true|secondWriteDelta=0|probe=MODEL-synchronization");
    }
}
