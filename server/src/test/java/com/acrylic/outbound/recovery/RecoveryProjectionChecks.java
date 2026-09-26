package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Python originals consumed by Java and projected through real MySQL transactions. */
public final class RecoveryProjectionChecks {
    /** Between the projected RELEASE_READY state and the fault checks that mutate shared state. */
    @FunctionalInterface public interface AfterReleaseReady { void run(JdbcTemplate jdbc, RecoveryTransactionFixture fixture) throws Exception; }

    public static void verify(JdbcTemplate jdbc, Path evidence) throws Exception {
        verify(jdbc, new RecoveryTransactionFixture(evidence.resolve("projection")), evidence, null);
    }

    /** Runs on a caller-owned control chain so later checks can continue the same closed-phase history. */
    public static void verify(JdbcTemplate jdbc, RecoveryTransactionFixture f, Path evidence) throws Exception {
        verify(jdbc, f, evidence, null);
    }

    public static void verify(JdbcTemplate jdbc, RecoveryTransactionFixture f, Path evidence, AfterReleaseReady afterReleaseReady)
            throws Exception {
        var databases = new RecoveryProjectionDatabases(jdbc);
        long baseline = jdbc.queryForObject("SELECT recovery_epoch FROM server_state", Long.class);
        assertThatThrownBy(() -> projection(jdbc, f, baseline + 1).project()).hasMessage("CONTROL_DATABASE_BASELINE_MISMATCH");
        assertEmpty(jdbc);
        concurrentRegistration(jdbc, f, baseline);
        var first = projection(jdbc, f, baseline); var initial = first.project();
        assertThat(initial.numericEpoch()).isEqualTo(Long.toString(baseline));
        assertClosed(initial); constraints(jdbc);
        RecoveryProjectionFaultChecks.atInitial(jdbc, f, databases, baseline);
        for (String phase : List.of("RECOVERING", "VERIFYING", "RELEASE_READY")) {            f.advancePhase(phase);
            assertThatThrownBy(first::project).hasMessage("CONTROL_INSTANCE_STALE");
            var current = projection(jdbc, f, baseline); var projected = current.project();
            assertThat(projected.phase()).isEqualTo(phase); assertClosed(projected);
            assertThat(projected.numericEpoch()).isEqualTo(Long.toString(baseline + 1));
            assertThat(current.project()).isEqualTo(projected);
            Files.writeString(evidence.resolve("projection-" + phase + ".json"),
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(projected));
        }
        assertThat(count(jdbc, "recovery_control_epoch")).isEqualTo(2);
        assertThat(count(jdbc, "recovery_control_projection")).isEqualTo(4);
        // The hook may legitimately change service_mode (initial NORMAL admission). The closed-phase
        // fault checks below require the projected RECOVERY_PENDING state, so restore it explicitly.
        String mode = jdbc.queryForObject("SELECT service_mode FROM server_state WHERE id=1", String.class);
        if (afterReleaseReady != null) afterReleaseReady.run(jdbc, f);
        jdbc.update("UPDATE server_state SET service_mode=? WHERE id=1", mode);
        RecoveryProjectionFaultChecks.atFinal(jdbc, f, databases, baseline);
        Files.writeString(evidence.resolve("closed-projection-checks.txt"),
            "PASS four phases/replay/concurrent registration/baseline/immutability/copy identity/rollback/overflow/gap; all capabilities closed\n");
    }

    static RecoveryClosedProjection projection(JdbcTemplate jdbc, RecoveryTransactionFixture f, long baseline) {
        return new RecoveryClosedProjection(jdbc.getDataSource(), f.location, f.checkpoint, baseline);
    }

    private static void concurrentRegistration(JdbcTemplate jdbc, RecoveryTransactionFixture f, long baseline) throws Exception {
        var one = projection(jdbc, f, baseline); var two = projection(jdbc, f, baseline);
        try (var blocker = jdbc.getDataSource().getConnection(); var pool = Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.createStatement()) { statement.executeQuery("SELECT * FROM server_state WHERE id=1 FOR UPDATE").close(); }
            try {
                var first = pool.submit(one::project); awaitWaiting(jdbc, 1);
                var second = pool.submit(two::project); awaitWaiting(jdbc, 2);
                // This case proves duplicate registration. Durable-close admission/drain has its
                // own transaction acceptance; publishing a close here intentionally forbids replay.
                blocker.rollback();
                assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
            } finally { blocker.rollback(); }
        }
        assertThat(count(jdbc, "recovery_installation_binding")).isEqualTo(1);
        assertThat(count(jdbc, "recovery_control_epoch")).isEqualTo(1);
        assertThat(count(jdbc, "recovery_control_projection")).isEqualTo(1);
    }

    private static void awaitWaiting(JdbcTemplate jdbc, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        do {
            int waiting = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=DATABASE() "
                + "AND INFO='SELECT recovery_epoch,service_mode FROM server_state WHERE id=1 FOR UPDATE'", Integer.class);
            if (waiting == expected) return;
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Projection did not reach concurrent database lock waits: " + expected);
    }

    private static void constraints(JdbcTemplate jdbc) {
        for (String table : List.of("recovery_installation_binding", "recovery_control_epoch", "recovery_control_projection")) {
            assertThatThrownBy(() -> jdbc.update("UPDATE " + table + " SET installation_id=installation_id"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("DELETE FROM " + table))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
        assertThatThrownBy(() -> jdbc.update("INSERT INTO recovery_control_projection SELECT installation_id,9,UNHEX(REPEAT('e',64)),"
            + "record_sha256,epoch_id,numeric_epoch+1,'VERIFYING',UTC_TIMESTAMP(6) FROM recovery_control_projection"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    static void assertEmpty(JdbcTemplate jdbc) {
        for (String table : List.of("recovery_installation_binding", "recovery_control_epoch", "recovery_control_projection"))
            assertThat(count(jdbc, table)).isZero();
    }

    static int count(JdbcTemplate jdbc, String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    private static void assertClosed(RecoveryClosedProjection.Result result) {
        assertThat(result.ordinaryWritesAllowed()).isFalse(); assertThat(result.localDeletionAllowed()).isFalse();
    }
}
