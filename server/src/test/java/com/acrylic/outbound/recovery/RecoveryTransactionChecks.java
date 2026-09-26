package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;

/** Real isolated MySQL transaction timing; no production routes or policy grants. */
public final class RecoveryTransactionChecks {
    public static void verify(JdbcTemplate jdbc, Path evidence) throws Exception {
        jdbc.execute("CREATE TABLE recovery_lease_probe(id INT PRIMARY KEY, counter INT NOT NULL) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE recovery_admitted_probe(id INT PRIMARY KEY, counter INT NOT NULL) ENGINE=InnoDB");
        jdbc.update("INSERT INTO recovery_lease_probe VALUES(1,0)");
        jdbc.update("INSERT INTO recovery_admitted_probe VALUES(1,0)");
        adapterRoundTrip(jdbc, evidence);
        var fixture = new RecoveryTransactionFixture(evidence.resolve("tx-normal"));
        var transaction = fixture.transaction(jdbc.getDataSource());
        commitRace(jdbc, fixture, transaction);
        fixture.assertAdvanced();
        assertThatThrownBy(() -> transaction.execute(status -> { throw new AssertionError("Stale callback ran"); }))
            .hasMessage("CONTROL_INSTANCE_STALE");
        var rollback = new RecoveryTransactionFixture(evidence.resolve("tx-rollback"));
        rollbackRace(jdbc, rollback, rollback.transaction(jdbc.getDataSource())); rollback.assertAdvanced();
        var ordinary = new RecoveryTransactionFixture(evidence.resolve("tx-errors"));
        var errors = ordinary.transaction(jdbc.getDataSource());
        sqlError(jdbc, ordinary, errors); outerTransaction(jdbc, errors); nestedTransaction(jdbc, errors);
        unknownCommit(jdbc, evidence);
        Files.writeString(evidence.resolve("leased-transaction-checks.txt"),
            "PASS commit/afterCommit/rollback/SQL-error/outer-and-nested-transaction/stale-instance/unknown-commit; no API gate integration\n");
    }

    private static void adapterRoundTrip(JdbcTemplate jdbc, Path evidence) throws Exception {
        var fixture = new RecoveryTransactionFixture(evidence.resolve("tx-adapter"));
        var admitted = new RecoveryAdmittedDataSource(jdbc.getDataSource(), fixture.location, fixture.checkpoint);
        try (var connection = admitted.getConnection()) {
            assertThatThrownBy(() -> connection.unwrap(DataSource.class))
                .hasMessage("CONTROL_CONNECTION_UNWRAP_FORBIDDEN");
        }
        new JdbcTemplate(admitted).update("UPDATE recovery_admitted_probe SET counter=5 WHERE id=1");
        assertThat(jdbc.queryForObject("SELECT counter FROM recovery_admitted_probe WHERE id=1", Integer.class)).isEqualTo(5);
        Files.writeString(evidence.resolve("admitted-datasource-check.txt"),
            "PASS real MySQL DataSource borrow/update/autocommit close; datasource unwrap blocked; authority checked before borrow\n");
    }

    private static void commitRace(JdbcTemplate jdbc, RecoveryTransactionFixture fixture,
            RecoveryLeasedTransaction transaction) throws Exception {
        var changed = new CountDownLatch(1); var commit = new CountDownLatch(1);
        var committed = new CountDownLatch(1); var cleanup = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var future = pool.submit(() -> transaction.execute(status -> {
                jdbc.update("UPDATE recovery_lease_probe SET counter=1 WHERE id=1");
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { committed.countDown(); await(cleanup); }
                });
                changed.countDown(); await(commit); return "committed";
            }));
            try {
                await(changed); assertCounter(jdbc, 0); fixture.assertBlocked();
                assertClosing(jdbc, fixture);
                commit.countDown(); await(committed); assertCounter(jdbc, 1); fixture.assertBlocked();
                assertThat(future.isDone()).isFalse(); cleanup.countDown();
                assertThat(future.get(15, TimeUnit.SECONDS)).isEqualTo("committed");
            } finally { commit.countDown(); cleanup.countDown(); }
        }
    }

    private static void rollbackRace(JdbcTemplate jdbc, RecoveryTransactionFixture fixture,
            RecoveryLeasedTransaction transaction) throws Exception {
        var changed = new CountDownLatch(1); var rollback = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var future = pool.submit(() -> transaction.execute(status -> {
                jdbc.update("UPDATE recovery_lease_probe SET counter=2 WHERE id=1");
                changed.countDown(); await(rollback); status.setRollbackOnly(); return null;
            }));
            try { await(changed); assertCounter(jdbc, 1); fixture.assertBlocked(); assertClosing(jdbc, fixture); }
            finally { rollback.countDown(); }
            future.get(15, TimeUnit.SECONDS); assertCounter(jdbc, 1);
        }
    }

    private static void sqlError(JdbcTemplate jdbc, RecoveryTransactionFixture fixture,
            RecoveryLeasedTransaction transaction) throws Exception {
        assertThatThrownBy(() -> transaction.execute(status -> {
            jdbc.update("UPDATE recovery_lease_probe SET counter=3 WHERE id=1");
            jdbc.update("INSERT INTO recovery_lease_probe VALUES(1,99)"); return null;
        })).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertCounter(jdbc, 1);
        // A subsequent transaction proves ordinary rollback did not poison the lifecycle adapter.
        transaction.execute(status -> { assertCounter(jdbc, 1); return null; });
    }

    private static void outerTransaction(JdbcTemplate jdbc, RecoveryLeasedTransaction transaction) {
        new TransactionTemplate(new JdbcTransactionManager(jdbc.getDataSource())).executeWithoutResult(status ->
            assertThatThrownBy(() -> transaction.execute(inner -> { throw new AssertionError("Nested callback ran"); }))
                .hasMessage("CONTROL_OUTER_TRANSACTION_FORBIDDEN"));
    }

    private static void unknownCommit(JdbcTemplate jdbc, Path evidence) throws Exception {
        var fixture = new RecoveryTransactionFixture(evidence.resolve("tx-unknown"));
        var source = new LostCommitDataSource(jdbc.getDataSource());
        var target = new JdbcTemplate(source);
        var transaction = fixture.transaction(source);
        assertThatThrownBy(() -> transaction.execute(status -> {
            target.update("UPDATE recovery_lease_probe SET counter=4 WHERE id=1"); return null;
        })).isInstanceOf(org.springframework.transaction.TransactionSystemException.class)
            .satisfies(error -> assertThat(error.getSuppressed()).anyMatch(
                suppressed -> "CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED".equals(suppressed.getMessage())));
        assertCounter(jdbc, 4); fixture.assertBlocked();
        assertThatThrownBy(() -> transaction.execute(status -> { throw new AssertionError("Poisoned callback ran"); }))
            .hasMessage("CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
    }

    private static void nestedTransaction(JdbcTemplate jdbc, RecoveryLeasedTransaction transaction) {
        var nested = new TransactionTemplate(new JdbcTransactionManager(jdbc.getDataSource()));
        nested.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThatThrownBy(() -> transaction.execute(status -> nested.execute(inner -> {
            throw new AssertionError("Independent nested transaction ran");
        }))).hasMessage("CONTROL_NESTED_TRANSACTION_FORBIDDEN");
        transaction.execute(status -> { assertCounter(jdbc, 1); return null; });
    }

    private static void assertCounter(JdbcTemplate jdbc, int value) {
        assertThat(jdbc.queryForObject("SELECT counter FROM recovery_lease_probe WHERE id=1", Integer.class)).isEqualTo(value);
    }

    private static void assertClosing(JdbcTemplate jdbc, RecoveryTransactionFixture fixture) {
        var fresh = fixture.transaction(jdbc.getDataSource());
        assertThatThrownBy(() -> fresh.execute(status -> { throw new AssertionError("New writer entered pending close"); }))
            .hasMessage("CONTROL_CLOSING_INTENT");
    }

    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
    }
}
