package com.acrylic.outbound.recovery;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.NestedRuntimeException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/**
 * Application-entry checks on one isolated input: the admitted writer is what JdbcTemplate and the
 * JDBC transaction manager use, an in-flight execution cannot be passed by a close, an independent
 * writer cannot advance while the entry holds the lease, and an unknown commit outcome keeps it.
 */
public final class RecoveryAdmissionEntryChecks {
    private RecoveryAdmissionEntryChecks() { }

    /** Production wiring against a live control authority. Terminal step: it leaves a pending intent. */
    public static void verifyAdmittedEntry(ConfigurableApplicationContext context, JdbcTemplate raw,
            RecoveryAdmissionEntryFixture fixture, Path evidence) throws Exception {
        DataSource admitted = context.getBean(DataSource.class);
        JdbcTemplate entry = context.getBean(JdbcTemplate.class);
        PlatformTransactionManager manager = context.getBean(PlatformTransactionManager.class);
        var transcript = new StringBuilder();

        // The entry serves one operation root, exactly like a request does: everything below runs
        // inside the root the entry admits, and the no-root refusal is checked on its own thread.
        RecoveryOperationLease root = admit(context);
        try {
            transcript.append(wiring(context, admitted, entry, manager));
            transcript.append(roundTrip(entry, manager, raw));
            transcript.append(closeRace(admitted, raw, evidence));
            transcript.append(fenceAndClosingIntent(context, admitted, entry, fixture, evidence));
        } finally {
            retire(root);
        }

        Files.writeString(evidence.resolve("admission-entry-checks.txt"), transcript.toString());
    }

    /** Production wiring again; terminal step: a connection-class failure during commit poisons it. */
    public static void verifyUnknownCommitEntry(ConfigurableApplicationContext context, JdbcTemplate raw,
            Path evidence) throws Exception {
        DataSource admitted = context.getBean(DataSource.class);
        JdbcTemplate entry = context.getBean(JdbcTemplate.class);
        assertThat(admitted).isInstanceOf(RecoveryRequestAdmittedDataSource.class);
        assertThat(entry.getDataSource()).isSameAs(admitted);
        var transcript = new StringBuilder("wiring: primary=" + admitted.getClass().getSimpleName()
            + " JdbcTemplate=admitted root=request-accepted\n");
        RecoveryOperationLease root = admit(context);
        try {
            transcript.append(connectionLossDuringCommit(entry,
                new TransactionTemplate(context.getBean(PlatformTransactionManager.class)), raw, evidence));
        } finally {
            retire(root);
        }
        Files.writeString(evidence.resolve("admission-entry-unknown-commit-checks.txt"), transcript.toString());
    }

    /** The same entry wiring with a lost commit acknowledgement; terminal step as well. */
    public static void verifyLostCommitEntry(ConfigurableApplicationContext context, JdbcTemplate raw,
            Path evidence) throws Exception {
        DataSource admitted = context.getBean(DataSource.class);
        JdbcTemplate entry = context.getBean(JdbcTemplate.class);
        var template = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThat(admitted).isInstanceOf(RecoveryRequestAdmittedDataSource.class);
        RecoveryOperationLease root = admit(context);
        try {
            int before = counter(raw);

            NestedRuntimeException failure = catchThrowableOfType(() -> template.executeWithoutResult(status ->
                entry.update("UPDATE recovery_entry_probe SET counter=counter+1 WHERE id=1")), NestedRuntimeException.class);
            assertThat(failure).as("a lost commit acknowledgement must surface as a failure").isNotNull();
            assertThat(counter(raw)).as("the real server commit happened, only its acknowledgement was lost")
                .isEqualTo(before + 1);
            assertThatThrownBy(() -> entry.update("INSERT INTO recovery_entry_probe VALUES(3,0)"))
                .as("an unknown outcome must hold the writer fence for the whole entry")
                .hasStackTraceContaining("CONTROL_WRITERS_ACTIVE");
            assertThat(raw.queryForObject("SELECT COUNT(*) FROM information_schema.INNODB_TRX", Integer.class)).isZero();
            Files.writeString(evidence.resolve("admission-entry-lost-commit.txt"),
                "observed=" + failure.getClass().getName() + ": " + failure.getMessage()
                    + "\ncommittedCounter=" + (before + 1) + "\nsubsequentEntryWrite=CONTROL_WRITERS_ACTIVE"
                    + "\ninnodbTrx=0\n");
        } finally {
            retire(root);
        }
    }

    /**
     * The entry's operations belong to one admitted root, so the checks admit one the way a request
     * does: through the registry, inside the serialized control section, with a bounded wait.
     */
    static RecoveryOperationLease admit(ConfigurableApplicationContext context) {
        var registry = context.getBean(RecoveryOperationRegistry.class);
        var token = new RecoveryOperationToken(java.util.UUID.randomUUID(), RecoveryOperationKind.HTTP_REQUEST,
            registry.startup().installationId(), registry.startup().recordSha256(), null);
        RecoveryOperationLease root = registry.admit(token);
        root.bindDispatch(Thread.currentThread());
        return root;
    }

    static void retire(RecoveryOperationLease root) {
        root.unbindAll();
        root.close();
    }

    private static String wiring(ConfigurableApplicationContext context, DataSource admitted,
            JdbcTemplate entry, PlatformTransactionManager manager) throws Exception {
        assertThat(admitted).isInstanceOf(RecoveryRequestAdmittedDataSource.class);
        assertThat(entry.getDataSource()).as("JdbcTemplate must not bypass the admitted writer").isSameAs(admitted);
        assertThat(manager).isInstanceOf(JdbcTransactionManager.class);
        assertThat(((JdbcTransactionManager) manager).getDataSource())
            .as("the JDBC transaction manager must not bypass the admitted writer").isSameAs(admitted);
        var pools = context.getBeansOfType(DataSource.class);
        assertThat(pools).hasSize(2);
        assertThat(pools.get(RecoveryAdmissionConfiguration.MIGRATION_DATASOURCE)).isInstanceOf(HikariDataSource.class);
        assertThat(pools.get(RecoveryAdmissionConfiguration.MIGRATION_DATASOURCE))
            .as("the migration-only pool must not be the entry datasource").isNotSameAs(admitted);
        // A thread without an operation root is refused instead of being served: the entry has no
        // connection-level fallback any more.
        assertThat(borrowWithoutRoot(admitted)).as("a thread without a root must be refused")
            .isEqualTo("RECOVERY_OPERATION_REQUIRED");
        // Deliberate bypass attempts have to fail instead of handing out the vendor object.
        assertThatThrownBy(() -> admitted.unwrap(HikariDataSource.class))
            .hasMessage("CONTROL_DATASOURCE_UNWRAP_FORBIDDEN");
        assertThat(admitted.unwrap(DataSource.class)).isSameAs(admitted);
        try (Connection borrowed = admitted.getConnection()) {
            assertThatThrownBy(() -> borrowed.unwrap(HikariDataSource.class))
                .hasMessage("CONTROL_CONNECTION_UNWRAP_FORBIDDEN");
            try (Statement statement = borrowed.createStatement()) {
                assertThatThrownBy(() -> statement.unwrap(HikariDataSource.class))
                    .hasMessage("CONTROL_JDBC_OBJECT_UNWRAP_FORBIDDEN");
            }
        }
        return "wiring: primary=" + admitted.getClass().getSimpleName()
            + " migration=" + RecoveryAdmissionConfiguration.MIGRATION_DATASOURCE
            + " JdbcTemplate/transactionManager=admitted unwrap=rejected rootRequired=RECOVERY_OPERATION_REQUIRED\n";
    }

    private static String borrowWithoutRoot(DataSource admitted) throws Exception {
        var pool = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "entry-without-root"));
        try {
            return pool.submit(() -> {
                try (Connection borrowed = admitted.getConnection()) { return "ADMITTED:" + borrowed; }
                catch (RuntimeException | SQLException error) { return error.getMessage(); }
            }).get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private static String roundTrip(JdbcTemplate entry, PlatformTransactionManager manager, JdbcTemplate raw) {
        entry.execute("CREATE TABLE IF NOT EXISTS recovery_entry_probe"
            + "(id INT PRIMARY KEY, counter INT NOT NULL) ENGINE=InnoDB");
        entry.update("DELETE FROM recovery_entry_probe");
        entry.update("INSERT INTO recovery_entry_probe VALUES(1,0)");
        var template = new TransactionTemplate(manager);
        template.executeWithoutResult(status -> entry.update("UPDATE recovery_entry_probe SET counter=counter+1 WHERE id=1"));
        assertThat(counter(raw)).isEqualTo(1);
        Throwable rolledBack = catchThrowable(() -> template.executeWithoutResult(status -> {
            entry.update("UPDATE recovery_entry_probe SET counter=99 WHERE id=1");
            throw new IllegalStateException("rollback probe");
        }));
        assertThat(rolledBack).isInstanceOf(IllegalStateException.class);
        assertThat(counter(raw)).as("the explicit rollback must be visible outside the entry").isEqualTo(1);
        return "roundTrip: committed=1 rolledBack=1 after explicit rollback\n";
    }

    private static String closeRace(DataSource admitted, JdbcTemplate raw, Path evidence) throws Exception {
        Connection borrowed = admitted.getConnection();
        // One proxy call per execution: the boundary serializes calls, so the check pins exactly one
        // in-flight delegate call and asserts that a concurrent close cannot pass it.
        Statement statement = borrowed.createStatement();
        var writer = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "entry-writer"));
        long waited;
        Object result;
        try {
            var started = new CountDownLatch(1);
            var outcome = new AtomicReference<Object>();
            Future<?> execution = writer.submit(() -> {
                started.countDown();
                try { outcome.set(statement.executeQuery("SELECT SLEEP(3)")); }
                catch (Throwable error) { outcome.set(error); }
            });
            assertThat(started.await(30, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(500);
            long begin = System.nanoTime();
            borrowed.close();
            waited = (System.nanoTime() - begin) / 1_000_000;
            execution.get(60, TimeUnit.SECONDS);
            result = outcome.get();
        } finally {
            writer.shutdownNow();
        }
        assertThat(result).as("the in-flight execution must not be poisoned by the concurrent close")
            .isInstanceOf(java.sql.ResultSet.class);
        assertThat(waited).as("close must wait for the in-flight admitted execution, not pass it")
            .isGreaterThanOrEqualTo(1500L);
        assertThat(borrowed.isClosed()).isTrue();
        assertThat(raw.queryForObject("SELECT COUNT(*) FROM information_schema.INNODB_TRX", Integer.class)).isZero();
        Files.writeString(evidence.resolve("admission-entry-close-race.txt"),
            "inFlight=SELECT SLEEP(3) closeWaitedMillis=" + waited + " delegateCallResult=ResultSet innodbTrx=0\n");
        return "closeRace: closeWaitedMillis=" + waited + " execution=ok innodbTrx=0\n";
    }

    /**
     * A pending closing intent refuses <b>new roots</b>; the root this entry already holds keeps
     * serving its own work (design §6.4), which is the behaviour the entry checks now pin.
     */
    private static String fenceAndClosingIntent(ConfigurableApplicationContext context, DataSource admitted,
            JdbcTemplate entry, RecoveryAdmissionEntryFixture fixture, Path evidence) throws Exception {
        try (Connection held = admitted.getConnection()) {
            // The independent writer records its durable stop intent first and then has to wait for
            // the lease this entry holds; no command record is written while the lease is held.
            fixture.assertIndependentWriterBlocked();
            // The admitted root keeps working: closing intent does not cut off retained work.
            try (Connection alsoHeld = admitted.getConnection()) {
                assertThat(alsoHeld).isNotNull();
            }
            entry.update("INSERT INTO recovery_entry_probe VALUES(4,0)");
            // A new root, however, is refused from that point on.
            assertThat(newRootRefusal(context)).isEqualTo("CONTROL_CLOSING_INTENT");
        }
        Files.writeString(evidence.resolve("admission-entry-fence.txt"),
            "heldLeaseVersusIndependentWriter=CONTROL_WRITERS_ACTIVE\n"
                + "admittedRootKeepsBorrowing=allowed\n"
                + "admittedRootKeepsWriting=allowed\n"
                + "newRootWithPendingIntent=CONTROL_CLOSING_INTENT\n");
        return "fence: independentWriterBlocked=CONTROL_WRITERS_ACTIVE"
            + " admittedRootStillServes=allowed newRootWithPendingIntent=CONTROL_CLOSING_INTENT\n";
    }

    private static String newRootRefusal(ConfigurableApplicationContext context) throws Exception {
        var pool = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "entry-new-root"));
        try {
            return pool.submit(() -> {
                try {
                    var registry = context.getBean(RecoveryOperationRegistry.class);
                    var token = new RecoveryOperationToken(java.util.UUID.randomUUID(), RecoveryOperationKind.HTTP_REQUEST,
                        registry.startup().installationId(), registry.startup().recordSha256(), null);
                    return "ADMITTED:" + registry.admit(token);
                } catch (RuntimeException error) {
                    return error.getMessage();
                }
            }).get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private static String connectionLossDuringCommit(JdbcTemplate entry, TransactionTemplate template,
            JdbcTemplate raw, Path evidence) throws Exception {
        int before = counter(raw);
        Throwable failure = catchThrowable(() -> template.executeWithoutResult(status -> {
            entry.update("UPDATE recovery_entry_probe SET counter=counter+10 WHERE id=1");
            raw.execute("KILL " + entry.queryForObject("SELECT CONNECTION_ID()", Long.class));
        }));
        assertThat(failure).as("a connection lost during commit must surface as a failure").isNotNull();
        assertThat(failure).isInstanceOf(NestedRuntimeException.class);
        assertThat(counter(raw)).as("the killed transaction was rolled back by the server").isEqualTo(before);
        assertThatThrownBy(() -> entry.update("INSERT INTO recovery_entry_probe VALUES(2,0)"))
            .as("a connection-class commit failure must hold the writer fence for the whole entry")
            .hasStackTraceContaining("CONTROL_WRITERS_ACTIVE");
        assertThat(raw.queryForObject("SELECT COUNT(*) FROM information_schema.INNODB_TRX", Integer.class)).isZero();
        Files.writeString(evidence.resolve("admission-entry-unknown-commit.txt"),
            "observed=" + failure.getClass().getName() + ": " + failure.getMessage()
                + "\ncounterAfterKilledTransaction=" + before
                + "\nsubsequentEntryWrite=CONTROL_WRITERS_ACTIVE\ninnodbTrx=0\n");
        return "unknownCommit: observed=" + failure.getClass().getSimpleName()
            + " subsequentEntryWrite=CONTROL_WRITERS_ACTIVE innodbTrx=0\n";
    }

    private static int counter(JdbcTemplate raw) {
        return raw.queryForObject("SELECT counter FROM recovery_entry_probe WHERE id=1", Integer.class);
    }
}
