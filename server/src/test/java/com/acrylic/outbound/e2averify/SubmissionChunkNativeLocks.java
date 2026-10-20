package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import java.sql.Connection;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Native row locks with observed performance_schema waiters, never timing-only inference. */
final class SubmissionChunkNativeLocks {
    private final JdbcTemplate raw;
    SubmissionChunkNativeLocks(JdbcTemplate raw) { this.raw = raw; }

    void hold(Connection connection, String table, String id) throws Exception {
        if (!Set.of("submission_scope", "app_user").contains(table)) throw new IllegalArgumentException();
        try (var query = connection.prepareStatement("SELECT id FROM " + table + " WHERE id=? FOR UPDATE")) {
            query.setBytes(1, Ids.bytes(UUID.fromString(id)));
            try (var rows = query.executeQuery()) { assertThat(rows.next()).isTrue(); }
        }
    }

    JsonNode waiting(String table, int minimum) {
        var observed = new AtomicInteger();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            observed.set(raw.queryForObject(
                "SELECT COUNT(DISTINCT l.THREAD_ID) FROM performance_schema.data_lock_waits w "
                + "JOIN performance_schema.data_locks l ON l.ENGINE=w.ENGINE "
                + "AND l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID "
                + "WHERE l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME=?", Integer.class, table));
            assertThat(observed.get()).isGreaterThanOrEqualTo(minimum);
        });
        return SubmissionJson.object().put("table", table).put("minimumWaiters", minimum)
            .put("observedDistinctWaiters", observed.get());
    }

    void holdIdentity(Connection connection) throws Exception {
        try (var query = connection.prepareStatement("SELECT id FROM user_management_guard WHERE id=1 FOR UPDATE")) {
            try (var rows = query.executeQuery()) { assertThat(rows.next()).isTrue(); }
        }
    }

    String actor(SubmissionChunkFaultWire wire) {
        return raw.queryForObject("SELECT BIN_TO_UUID(operator_id) FROM submission WHERE id=?", String.class,
            SubmissionChunkFaultState.id(wire));
    }

    long authVersion(String actor) {
        return raw.queryForObject("SELECT auth_version FROM app_user WHERE id=?", Long.class,
            Ids.bytes(UUID.fromString(actor)));
    }

    void bumpAuthVersion(Connection connection, String actor, long version) throws Exception {
        try (var update = connection.prepareStatement("UPDATE app_user SET auth_version=? WHERE id=?")) {
            update.setLong(1, version); update.setBytes(2, Ids.bytes(UUID.fromString(actor)));
            assertThat(update.executeUpdate()).isEqualTo(1);
        }
    }
}
