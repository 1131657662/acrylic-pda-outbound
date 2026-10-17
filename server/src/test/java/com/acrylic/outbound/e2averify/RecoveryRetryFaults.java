package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Isolated native SQL faults; injections are removed before subsequent scenarios. */
final class RecoveryRetryFaults {
    private final RecoveryRetryScenario f;
    RecoveryRetryFaults(RecoveryRetryScenario fixture) { f = fixture; }

    void concurrent() throws Exception {
        String key = UUID.randomUUID().toString(), original = f.originals.getFirst();
        var first = peer("first"); var second = peer("second");
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { ready.countDown(); assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return first.retry(f.path(original), key, 200).at("/data/id").asText(); });
            var b = pool.submit(() -> { ready.countDown(); assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return second.retry(f.path(original), key, 200).at("/data/id").asText(); });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            String id = a.get(45, TimeUnit.SECONDS); assertThat(b.get(45, TimeUnit.SECONDS)).isEqualTo(id);
            assertThat(count(key)).isEqualTo(1); f.assertSnapshot(original, id); f.preserve(id, "p5-concurrent");
            save("concurrent", SubmissionJson.object().put("key", key).put("bothId", id).put("jobs", count(key)));
        }
        f.http.retry(f.path(f.originals.getLast()), key, 409);
    }

    void rollback() throws Exception {
        var raw = f.supply.raw(); String key = UUID.randomUUID().toString(), original = f.originals.getFirst();
        int events = raw.queryForObject("SELECT COUNT(*) FROM operation_event WHERE event_type='REPORT_EXPORT_RETRIED'", Integer.class);
        raw.execute("CREATE TABLE d13_retry_tail_guard (id INT NOT NULL PRIMARY KEY) ENGINE=InnoDB");
        raw.update("INSERT INTO d13_retry_tail_guard VALUES (1)");
        raw.execute("CREATE TRIGGER d13_fail_retry_audit BEFORE INSERT ON operation_event FOR EACH ROW "
            + "BEGIN IF NEW.event_type='REPORT_EXPORT_RETRIED' THEN INSERT INTO d13_retry_tail_guard VALUES (1); END IF; END");
        com.fasterxml.jackson.databind.JsonNode failure;
        try { failure = f.http.retry(f.path(original), key, 409);
            assertThat(failure.path("code").asText()).isEqualTo("WRITE_CONFLICT"); }
        finally { raw.execute("DROP TRIGGER d13_fail_retry_audit"); raw.execute("DROP TABLE d13_retry_tail_guard"); }
        assertThat(count(key)).isZero();
        assertThat(raw.queryForObject("SELECT COUNT(*) FROM operation_event WHERE event_type='REPORT_EXPORT_RETRIED'", Integer.class)).isEqualTo(events);
        String id = f.http.retry(f.path(original), key, 200).at("/data/id").asText();
        f.assertSnapshot(original, id); f.preserve(id, "p5-tail-recovered");
        save("rollback", SubmissionJson.object().put("key", key).put("rolledBack", true).put("recoveredId", id)
            .put("injection", "native duplicate primary key in final audit trigger")
            .put("failureRequestId", failure.path("requestId").asText()).put("failureCode", failure.path("code").asText())
            .put("jobsAfterFailure", 0).put("eventsBeforeFailure", events).put("eventsAfterFailure", events));
    }

    void revokedAfterLockWait() throws Exception {
        String key = UUID.randomUUID().toString(), user = f.userId();
        var http = peer("revoked"); var raw = f.supply.raw();
        boolean changed = false;
        try (var connection = raw.getDataSource().getConnection(); var pool = Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            lock(connection, user);
            try {
                var response = pool.submit(() -> http.retry(f.path(f.originals.getFirst()), key, 401));
                await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(waiters()).isPositive());
                try (var update = connection.prepareStatement("UPDATE app_user SET auth_version=auth_version+1 WHERE id=UUID_TO_BIN(?)")) {
                    update.setString(1, user); assertThat(update.executeUpdate()).isEqualTo(1);
                }
                connection.commit();
                changed = true;
                assertThat(response.get(30, TimeUnit.SECONDS).path("code").asText()).isEqualTo("SESSION_REVOKED");
                assertThat(count(key)).isZero();
                save("revoked", SubmissionJson.object().put("key", key).put("lockWaitObserved", true).put("noNewJob", true));
            } finally { connection.rollback(); }
        } finally { if (changed) raw.update("UPDATE app_user SET auth_version=auth_version-1 WHERE id=UUID_TO_BIN(?)", user); }
    }

    private long waiters() {
        return f.supply.raw().queryForObject("""
            SELECT COUNT(*) FROM performance_schema.data_lock_waits w
            JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID
            WHERE l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME='app_user'
            """, Long.class);
    }

    private static void lock(java.sql.Connection connection, String user) throws Exception {
        try (var query = connection.prepareStatement("SELECT id FROM app_user WHERE id=UUID_TO_BIN(?) FOR UPDATE")) {
            query.setString(1, user); try (var row = query.executeQuery()) { assertThat(row.next()).isTrue(); }
        }
    }

    private RecoveryWorkerHttp peer(String suffix) throws Exception {
        var peer = new RecoveryWorkerHttp(f.supply.profile().attempt.resolve("private/http-retry-" + suffix));
        peer.login(f.supply.input().username()); return peer;
    }

    private int count(String key) {
        return f.supply.raw().queryForObject("SELECT COUNT(*) FROM background_job WHERE dedupe_key=?", Integer.class,
            "r08:" + f.userId() + ":" + key);
    }

    private void save(String name, com.fasterxml.jackson.databind.JsonNode facts) throws Exception {
        Files.writeString(f.supply.profile().attempt.resolve("retry-" + name + ".json"), facts.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }
}
