package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.recovery.RecoveryOperationRegistry;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.sql.Connection;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Native file-before-DB wait, final SQL rollback and post-wait revocation. */
final class RecoveryRelayFaults {
    private final RecoveryRelayScenario f;
    RecoveryRelayFaults(RecoveryRelayScenario fixture) { f=fixture; }
    void verify() throws Exception { concurrent();held();rollback();revoked(); }

    private void concurrent() throws Exception {
        byte[] bytes=f.input.archive();String key=UUID.randomUUID().toString();
        var a=f.peer("first");a.login(f.input.username());var b=f.peer("second");b.login(f.input.username());
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var x=pool.submit(()->{ready.countDown();start.await();return f.upload(a,bytes,key,200).path("data");});
            var y=pool.submit(()->{ready.countDown();start.await();return f.upload(b,bytes,key,200).path("data");});
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            var receipt=x.get(30,TimeUnit.SECONDS);assertThat(y.get(30,TimeUnit.SECONDS)).isEqualTo(receipt);
            assertThat(count(key)).isEqualTo(1);f.preserve(receipt,bytes,"concurrent-content");
            f.save("concurrent",SubmissionJson.object().put("key",key).put("id",receipt.path("relayId").asText()).put("records",1));
        }
    }

    private void held() throws Exception {
        byte[] bytes=f.input.archive();String key=UUID.randomUUID().toString();
        var peer=f.peer("held");peer.login(f.input.username());
        try(var c=f.raw.getDataSource().getConnection();var pool=Executors.newSingleThreadExecutor()) {
            c.setAutoCommit(false);lock(c,"device",f.input.device());
            try {
                var reply=pool.submit(()->f.upload(peer,bytes,key,200).path("data"));waiting("device");
                assertThat(Files.readAllBytes(f.file(bytes))).isEqualTo(bytes);
                assertThat(f.web.getBean(RecoveryOperationRegistry.class).liveRoots()).isPositive();
                Files.copy(f.profile.attempt.resolve("private/p5-retirement/p5/leases/index.json"),
                    f.profile.attempt.resolve("relay-held-index.json"));
                f.probe("BLOCKED","held");c.commit();var receipt=reply.get(30,TimeUnit.SECONDS);
                f.preserve(receipt,bytes,"held-content");
                f.save("held",SubmissionJson.object().put("key",key).put("id",receipt.path("relayId").asText())
                    .put("actualLockWait",true).put("fileBeforeCommit",true).put("independentWriterBlocked",true));
            } finally { c.rollback(); }
        }
    }

    private void rollback() throws Exception {
        byte[] bytes=f.input.archive();String key=UUID.randomUUID().toString();
        int relays=f.raw.queryForObject("SELECT COUNT(*) FROM original_relay",Integer.class);
        int events=f.raw.queryForObject("SELECT COUNT(*) FROM operation_event",Integer.class);
        f.raw.execute("CREATE TABLE d13_relay_tail_guard(id INT PRIMARY KEY) ENGINE=InnoDB");
        f.raw.update("INSERT INTO d13_relay_tail_guard VALUES(1)");
        f.raw.execute("CREATE TRIGGER d13_relay_tail BEFORE INSERT ON api_idempotency_request FOR EACH ROW BEGIN "
            +"IF NEW.route_scope='relay.receive' THEN INSERT INTO d13_relay_tail_guard VALUES(1); END IF; END");
        try { assertThat(f.upload(f.http,bytes,key,409).path("code").asText()).isEqualTo("WRITE_CONFLICT"); }
        finally { f.raw.execute("DROP TRIGGER d13_relay_tail");f.raw.execute("DROP TABLE d13_relay_tail_guard"); }
        assertThat(count(key)).isZero();
        assertThat(f.raw.queryForObject("SELECT COUNT(*) FROM original_relay",Integer.class)).isEqualTo(relays);
        assertThat(f.raw.queryForObject("SELECT COUNT(*) FROM operation_event",Integer.class)).isEqualTo(events);
        var receipt=f.upload(f.http,bytes,key,200).path("data");f.preserve(receipt,bytes,"tail-recovered");
        f.save("rollback",SubmissionJson.object().put("key",key).put("id",receipt.path("relayId").asText())
            .put("transactionRolledBack",true).put("relaysAfterFailure",relays).put("eventsAfterFailure",events)
            .put("idempotencyRowsAfterFailure",0));
    }

    private void revoked() throws Exception {
        byte[] bytes=f.input.archive();String key=UUID.randomUUID().toString();
        var peer=f.peer("revoked");peer.login(f.input.username());boolean changed=false;
        try(var c=f.raw.getDataSource().getConnection();var pool=Executors.newSingleThreadExecutor()) {
            c.setAutoCommit(false);lock(c,"app_user",f.input.user());
            try {
                var reply=pool.submit(()->f.upload(peer,bytes,key,401));waiting("app_user");
                try(var q=c.prepareStatement("UPDATE app_user SET auth_version=auth_version+1 WHERE id=UUID_TO_BIN(?)")) {
                    q.setString(1,f.input.user());assertThat(q.executeUpdate()).isEqualTo(1);
                }
                c.commit();changed=true;
                assertThat(reply.get(30,TimeUnit.SECONDS).path("code").asText()).isEqualTo("SESSION_REVOKED");
                assertThat(count(key)).isZero();
                assertThat(f.raw.queryForObject("SELECT COUNT(*) FROM original_relay WHERE archive_sha256=?",
                    Integer.class,ImportFiles.sha256(bytes))).isZero();
                f.save("revoked",SubmissionJson.object().put("key",key).put("actualLockWait",true).put("noRelay",true));
            } finally { c.rollback(); }
        } finally { if(changed)f.raw.update("UPDATE app_user SET auth_version=auth_version-1 WHERE id=UUID_TO_BIN(?)",f.input.user()); }
    }

    private int count(String key) {
        return f.raw.queryForObject("SELECT COUNT(*) FROM api_idempotency_request WHERE principal_id=UUID_TO_BIN(?) "
            +"AND route_scope='relay.receive' AND idempotency_key=?",Integer.class,f.input.user(),key);
    }
    private void waiting(String table) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(()->assertThat(f.raw.queryForObject(
            "SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l "
            +"ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID WHERE l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME=?",
            Integer.class,table)).isPositive());
    }
    private static void lock(Connection c,String table,String id) throws Exception {
        if(!Set.of("device","app_user").contains(table))throw new IllegalArgumentException();
        try(var q=c.prepareStatement("SELECT id FROM "+table+" WHERE id=UUID_TO_BIN(?) FOR UPDATE")) {
            q.setString(1,id);try(var rows=q.executeQuery()) { assertThat(rows.next()).isTrue(); }
        }
    }
}
