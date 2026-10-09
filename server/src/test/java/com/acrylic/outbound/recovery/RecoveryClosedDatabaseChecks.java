package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Real isolated MySQL probe writes. Synthetic source bytes never stand in for reconstructed business facts. */
public final class RecoveryClosedDatabaseChecks {
    public static void verify(JdbcTemplate jdbc, Path attempt) throws Exception {
        var source = RecoverySourceFixture.forRestore(attempt); RecoveryClosedTestSupply.writers(source.control);
        long baseline = jdbc.queryForObject("SELECT recovery_epoch FROM server_state WHERE id=1", Long.class);
        new RecoveryClosedProjection(jdbc.getDataSource(), RecoveryClosedTestSupply.of(source).authority(), baseline).project();
        source.advance(); var supply = RecoveryClosedTestSupply.of(source); var authority = supply.authority();
        var original = source.read();
        var pending = new RecoveryClosedTransaction(jdbc.getDataSource(), authority);
        assertThatThrownBy(() -> pending.execute(original, status -> { throw new AssertionError("Unprojected callback"); }))
            .hasMessage("CONTROL_DATABASE_PROJECTION_MISMATCH");
        new RecoveryClosedProjection(jdbc.getDataSource(), RecoveryClosedTestSupply.of(source).authority(), baseline).project();
        jdbc.execute("CREATE TABLE closed_recovery_probe(id INT PRIMARY KEY,counter INT NOT NULL) ENGINE=InnoDB");
        jdbc.update("INSERT INTO closed_recovery_probe VALUES(1,0)");
        var transaction = new RecoveryClosedTransaction(jdbc.getDataSource(), authority);
        transaction.execute(original, status -> { jdbc.update("UPDATE closed_recovery_probe SET counter=1 WHERE id=1"); return null; });
        assertCounter(jdbc, 1);
        lastSqlRollback(jdbc, transaction, original); wrongState(jdbc, transaction, original);
        wrongSource(source, transaction); concurrent(jdbc, transaction, original);
        RecoveryClosedDatabaseFaultChecks.verify(jdbc, authority, original);
        unknownCommit(jdbc, source, original);
        Files.writeString(attempt.resolve("closed-authority-root.txt"), authority.authorityRoot().toString());
        Files.writeString(attempt.resolve("closed-control-root.txt"), source.control.root.toString());
        Files.writeString(attempt.resolve("closed-database-checks.txt"),
            "PASS independently pinned synthetic closed authority; real projection/commit/rollback/concurrent refusal/instance/copy/unknown commit; probe only\n");
    }

    private static void lastSqlRollback(JdbcTemplate jdbc, RecoveryClosedTransaction transaction, RecoveryPinnedSource original) {
        assertThatThrownBy(() -> transaction.execute(original, status -> {
            jdbc.update("UPDATE closed_recovery_probe SET counter=3 WHERE id=1");
            jdbc.update("INSERT INTO closed_recovery_probe VALUES(1,99)"); return null;
        })).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertCounter(jdbc, 1);
        transaction.execute(original, status -> { status.setRollbackOnly(); return null; }); assertCounter(jdbc, 1);
    }

    private static void wrongState(JdbcTemplate jdbc, RecoveryClosedTransaction transaction, RecoveryPinnedSource original) {
        jdbc.update("UPDATE server_state SET service_mode='NORMAL' WHERE id=1");
        try { assertThatThrownBy(() -> transaction.execute(original, status -> null))
            .hasMessage("CONTROL_DATABASE_PROJECTION_MISMATCH"); }
        finally { jdbc.update("UPDATE server_state SET service_mode='RECOVERY_PENDING' WHERE id=1"); }
        assertCounter(jdbc, 1);
    }

    private static void wrongSource(RecoverySourceFixture source, RecoveryClosedTransaction transaction) throws Exception {
        var other = RecoverySourceFixture.related(source, "TEMP").read();
        assertThatThrownBy(() -> transaction.execute(other, status -> { throw new AssertionError("Foreign callback"); }))
            .hasMessage("CONTROL_CLOSED_SOURCE_MISMATCH");
    }

    private static void concurrent(JdbcTemplate jdbc, RecoveryClosedTransaction transaction, RecoveryPinnedSource source) throws Exception {
        var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> transaction.execute(source, status -> {
                jdbc.update("UPDATE closed_recovery_probe SET counter=2 WHERE id=1"); entered.countDown(); await(finish); return null;
            }));
            try {
                await(entered); assertCounter(jdbc, 1);
                var second = pool.submit(() -> assertThatThrownBy(() -> transaction.execute(source, status -> {
                    throw new AssertionError("Concurrent writer callback");
                })).hasMessage("CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED"));
                second.get(15, TimeUnit.SECONDS);
            } finally { finish.countDown(); }
            first.get(15, TimeUnit.SECONDS); assertCounter(jdbc, 2);
        }
    }

    private static void unknownCommit(JdbcTemplate jdbc, RecoverySourceFixture source, RecoveryPinnedSource original) throws Exception {
        var supply = RecoveryClosedTestSupply.of(source); var lost = new LostCommitDataSource(jdbc.getDataSource());
        var transaction = new RecoveryClosedTransaction(lost, supply.authority()); var target = new JdbcTemplate(lost);
        assertThatThrownBy(() -> transaction.execute(original, status -> {
            target.update("UPDATE closed_recovery_probe SET counter=7 WHERE id=1"); return null;
        })).isInstanceOf(org.springframework.transaction.TransactionSystemException.class);
        assertCounter(jdbc, 7);
        assertThatThrownBy(supply::authority).hasMessage("CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
        assertThatThrownBy(() -> transaction.execute(original, status -> null)).hasMessage("CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
    }

    private static void assertCounter(JdbcTemplate jdbc, int expected) {
        assertThat(jdbc.queryForObject("SELECT counter FROM closed_recovery_probe WHERE id=1", Integer.class)).isEqualTo(expected);
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
    }
}
