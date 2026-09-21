package com.acrylic.outbound.foundationmigration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

final class FoundationMetadataLock {
    private final JdbcTemplate jdbc;
    FoundationMetadataLock(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void verify() throws Exception {
        holdUntilTimeout();
        new LegacySnapshot(jdbc).assertValuesAndFiles("v33-original.json");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME='submission_scope' AND COLUMN_NAME='mapping_version'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='34' AND success=0", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> Flyway.configure().dataSource(jdbc.getDataSource()).target("34").load().migrate())
                .hasMessageContaining("failed migration");
    }

    private void holdUntilTimeout() throws Exception {
        try (var holder = Objects.requireNonNull(jdbc.getDataSource()).getConnection(); var pool = Executors.newSingleThreadExecutor()) {
            holder.setAutoCommit(false);
            long connection;
            try (var statement = holder.createStatement(); var result = statement.executeQuery("SELECT CONNECTION_ID(),id FROM submission_scope LIMIT 1")) {
                assertThat(result.next()).isTrue(); connection = result.getLong(1);
            }
            long started = System.nanoTime();
            var pending = pool.submit(() -> Flyway.configure().dataSource(jdbc.getDataSource()).target("34").load().migrate());
            try {
                var locks = waiting();
                assertThat(locks.stream().anyMatch(row -> ((Number) row.get("PROCESSLIST_ID")).longValue() == connection
                        && "GRANTED".equals(row.get("LOCK_STATUS")))).as("Actual holder metadata lock").isTrue();
                assertThat(locks.stream().anyMatch(row -> ((Number) row.get("PROCESSLIST_ID")).longValue() != connection
                        && "PENDING".equals(row.get("LOCK_STATUS")))).as("Distinct waiting migration connection").isTrue();
                Files.writeString(FoundationEnvironment.evidence("negative-lock-wait.json"), new ObjectMapper().writeValueAsString(
                        Map.of("holderConnectionId", connection, "locks", locks, "database", FoundationEnvironment.database())));
                assertThatThrownBy(() -> pending.get(20, TimeUnit.SECONDS)).hasCauseInstanceOf(org.flywaydb.core.api.FlywayException.class)
                        .hasStackTraceContaining("Lock wait timeout exceeded");
                long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                assertThat(elapsed).isGreaterThanOrEqualTo(9000);
                Files.writeString(FoundationEnvironment.evidence("negative-lock-outcome.json"), "{\"elapsedMillis\":" + elapsed + "}");
            } finally {
                try { if (!pending.isDone()) pending.get(15, TimeUnit.SECONDS); }
                catch (ExecutionException expectedFailure) { /* Retain the DDL timeout before releasing the blocking transaction. */ }
                finally { holder.rollback(); }
            }
        }
    }

    private List<Map<String, Object>> waiting() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            var locks = jdbc.queryForList("SELECT t.PROCESSLIST_ID,l.LOCK_TYPE,l.LOCK_DURATION,l.LOCK_STATUS,l.OBJECT_SCHEMA,l.OBJECT_NAME "
                    + "FROM performance_schema.metadata_locks l JOIN performance_schema.threads t ON t.THREAD_ID=l.OWNER_THREAD_ID "
                    + "WHERE l.OBJECT_SCHEMA=? AND l.OBJECT_NAME='submission_scope'", FoundationEnvironment.database());
            if (locks.stream().anyMatch(row -> "PENDING".equals(row.get("LOCK_STATUS")))) return locks;
            Thread.sleep(25);
        }
        throw new AssertionError("No actual pending metadata lock observed");
    }
}
