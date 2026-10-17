package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.RecoveryOperationRegistry;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.sql.Connection;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Native locks and final audit constraint failure on this disposable database only. */
final class RecoveryImportFaults {
    private final RecoveryImportScenario f;
    RecoveryImportFaults(RecoveryImportScenario fixture) { f=fixture; }
    void verify() throws Exception { concurrent(); held(); rollback(); revoked(); }

    private void concurrent() throws Exception {
        byte[] bytes = f.bytes(3); String key = UUID.randomUUID().toString();
        var a=f.peer("first"); a.login(f.input.username()); var b=f.peer("second"); b.login(f.input.username());
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var pool=Executors.newFixedThreadPool(2)) {
            var x=pool.submit(() -> { ready.countDown(); start.await(); return f.upload(a,bytes,key,200).at("/data/id").asText(); });
            var y=pool.submit(() -> { ready.countDown(); start.await(); return f.upload(b,bytes,key,200).at("/data/id").asText(); });
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            String id=x.get(30,TimeUnit.SECONDS); assertThat(y.get(30,TimeUnit.SECONDS)).isEqualTo(id);
            assertThat(count(key)).isEqualTo(1); f.preserve(id,bytes,"concurrent-content");
            f.save("concurrent",SubmissionJson.object().put("key",key).put("id",id).put("records",1));
        }
    }

    private void held() throws Exception {
        byte[] bytes=f.bytes(4); String key=UUID.randomUUID().toString();
        var peer=f.peer("held"); peer.login(f.input.username());
        try (var c=f.raw.getDataSource().getConnection(); var pool=Executors.newSingleThreadExecutor()) {
            c.setAutoCommit(false); lock(c,"warehouse",f.input.warehouse());
            try {
                var reply=pool.submit(() -> f.upload(peer,bytes,key,200).at("/data/id").asText());
                waiting("warehouse"); assertThat(Files.readAllBytes(f.file(bytes))).isEqualTo(bytes);
                assertThat(f.web.getBean(RecoveryOperationRegistry.class).liveRoots()).isPositive();
                Files.copy(f.profile.attempt.resolve("private/p5-retirement/p5/leases/index.json"),
                    f.profile.attempt.resolve("import-held-index.json"));
                f.probe("BLOCKED","held"); c.commit();
                String id=reply.get(30,TimeUnit.SECONDS); f.preserve(id,bytes,"held-content");
                f.save("held",SubmissionJson.object().put("key",key).put("id",id).put("actualLockWait",true)
                    .put("fileBeforeCommit",true).put("independentWriterBlocked",true));
            } finally { c.rollback(); }
        }
    }

    private void rollback() throws Exception {
        byte[] bytes=f.bytes(5); String key=UUID.randomUUID().toString();
        int imports=f.raw.queryForObject("SELECT COUNT(*) FROM inventory_import",Integer.class);
        int events=f.raw.queryForObject("SELECT COUNT(*) FROM operation_event",Integer.class);
        f.raw.execute("CREATE TABLE d13_import_tail_guard(id INT PRIMARY KEY) ENGINE=InnoDB");
        f.raw.update("INSERT INTO d13_import_tail_guard VALUES(1)");
        f.raw.execute("CREATE TRIGGER d13_import_tail BEFORE INSERT ON operation_event FOR EACH ROW BEGIN "
            + "IF NEW.event_type='INVENTORY_PREFLIGHT' THEN INSERT INTO d13_import_tail_guard VALUES(1); END IF; END");
        try { assertThat(f.upload(f.http,bytes,key,409).path("code").asText()).isEqualTo("WRITE_CONFLICT"); }
        finally { f.raw.execute("DROP TRIGGER d13_import_tail"); f.raw.execute("DROP TABLE d13_import_tail_guard"); }
        assertThat(count(key)).isZero();
        assertThat(f.raw.queryForObject("SELECT COUNT(*) FROM inventory_import",Integer.class)).isEqualTo(imports);
        assertThat(f.raw.queryForObject("SELECT COUNT(*) FROM operation_event",Integer.class)).isEqualTo(events);
        String id=f.upload(f.http,bytes,key,200).at("/data/id").asText(); f.preserve(id,bytes,"tail-recovered");
        f.save("rollback",SubmissionJson.object().put("key",key).put("id",id).put("transactionRolledBack",true)
            .put("importsAfterFailure",imports).put("eventsAfterFailure",events).put("idempotencyRowsAfterFailure",0));
    }

    private void revoked() throws Exception {
        byte[] bytes=f.bytes(6); String key=UUID.randomUUID().toString();
        var peer=f.peer("revoked"); peer.login(f.input.username()); boolean changed=false;
        try (var c=f.raw.getDataSource().getConnection(); var pool=Executors.newSingleThreadExecutor()) {
            c.setAutoCommit(false); lock(c,"app_user",f.input.user());
            try {
                var reply=pool.submit(() -> f.upload(peer,bytes,key,401)); waiting("app_user");
                try (var q=c.prepareStatement("UPDATE app_user SET auth_version=auth_version+1 WHERE id=UUID_TO_BIN(?)")) {
                    q.setString(1,f.input.user()); assertThat(q.executeUpdate()).isEqualTo(1);
                }
                c.commit(); changed=true;
                assertThat(reply.get(30,TimeUnit.SECONDS).path("code").asText()).isEqualTo("SESSION_REVOKED");
                assertThat(count(key)).isZero();
                assertThat(f.raw.queryForObject("SELECT COUNT(*) FROM inventory_import WHERE file_sha256=?",Integer.class,
                    com.acrylic.outbound.inventory.ImportFiles.sha256(bytes))).isZero();
                f.save("revoked",SubmissionJson.object().put("key",key).put("actualLockWait",true).put("noImport",true));
            } finally { c.rollback(); }
        } finally { if(changed) f.raw.update("UPDATE app_user SET auth_version=auth_version-1 WHERE id=UUID_TO_BIN(?)",f.input.user()); }
    }

    private int count(String key) {
        return f.raw.queryForObject("SELECT COUNT(*) FROM api_idempotency_request WHERE principal_id=UUID_TO_BIN(?) "
            + "AND route_scope='inventory.import' AND idempotency_key=?",Integer.class,f.input.user(),key);
    }

    private void waiting(String table) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(f.raw.queryForObject(
            "SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l "
            + "ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID WHERE l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME=?",
            Integer.class,table)).isPositive());
    }

    private static void lock(Connection c,String table,String id) throws Exception {
        if(!Set.of("warehouse","app_user").contains(table)) throw new IllegalArgumentException();
        try(var q=c.prepareStatement("SELECT id FROM " + table + " WHERE id=UUID_TO_BIN(?) FOR UPDATE")) {
            q.setString(1,id); try(var rows=q.executeQuery()) { assertThat(rows.next()).isTrue(); }
        }
    }
}
