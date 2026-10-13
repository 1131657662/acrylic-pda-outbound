package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

final class IndependentCredentialBoundaryChecks {
    private IndependentCredentialBoundaryChecks() { }
    static void verify(IndependentCredentialHttpWire wire,JsonNode original,UUID originalKey) throws Exception {
        invalidInputs(wire);crossDevice(wire,original,originalKey);tailRollback(wire);
        lockThenRevoke(wire);assertThat(wire.count()).isEqualTo(2);
    }
    private static void invalidInputs(IndependentCredentialHttpWire wire) throws Exception {
        for(var body:java.util.List.of(wire.command().deepCopy().put("operatorId",UUID.randomUUID().toString()),
            wire.command().deepCopy().put("deviceKeySha256","bad")))
            assertThat(wire.post("/pda/independent-credentials",body,UUID.randomUUID()).statusCode()).isEqualTo(400);
        var mismatch=wire.command().deepCopy().put("deviceKeySha256","b".repeat(64));
        assertThat(wire.post("/pda/independent-credentials",mismatch,UUID.randomUUID()).statusCode()).isEqualTo(409);
        wire.epoch="2";assertThat(wire.issue(UUID.randomUUID()).statusCode()).isEqualTo(409);wire.epoch="1";
        wire.jdbc.update("UPDATE server_state SET service_mode='RECOVERY_PENDING' WHERE id=1");
        assertThat(wire.issue(UUID.randomUUID()).statusCode()).isEqualTo(503);
        wire.jdbc.update("UPDATE server_state SET service_mode='NORMAL' WHERE id=1");
    }
    private static void crossDevice(IndependentCredentialHttpWire wire,JsonNode original,UUID key) throws Exception {
        var other=wire.pda.register();wire.login(other);
        assertThat(wire.get(original.path("credentialId").asText()).statusCode()).isEqualTo(404);
        var body=SubmissionJson.object().put("deviceId",other.id()).put("deviceKeySha256",ImportFiles.sha256(other.key().getPublic().getEncoded()));
        var mismatch=wire.post("/pda/independent-credentials",body,key);assertThat(mismatch.statusCode()).isEqualTo(409);
        assertThat(wire.code(mismatch)).isEqualTo("IDEMPOTENCY_CONTENT_MISMATCH");wire.login(wire.device);
        assertThat(wire.data(wire.issue(key),200)).isEqualTo(original);
    }
    private static void tailRollback(IndependentCredentialHttpWire wire) throws Exception {
        UUID key=UUID.randomUUID();wire.jdbc.execute("""
            CREATE TRIGGER e06_identity_tail_failure BEFORE INSERT ON operation_event FOR EACH ROW
            BEGIN IF NEW.event_type='INDEPENDENT_CREDENTIAL_ISSUED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='E06_SYNTHETIC_TAIL_FAILURE'; END IF; END
            """);
        try { assertThat(wire.issue(key).statusCode()).isEqualTo(500);assertThat(wire.count()).isEqualTo(1);
            assertThat(wire.jdbc.queryForObject("SELECT COUNT(*) FROM api_idempotency_request WHERE idempotency_key=?",Long.class,key.toString())).isZero(); }
        finally { wire.jdbc.execute("DROP TRIGGER e06_identity_tail_failure"); }
        assertThat(wire.data(wire.issue(key),200).path("credentialId").asText()).isNotBlank();
        assertThat(wire.count()).isEqualTo(2);
    }
    private static void lockThenRevoke(IndependentCredentialHttpWire wire) throws Exception {
        // This transaction models an in-flight role mutation holding the established identity guard.
        try(var connection=wire.jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try(var statement=connection.createStatement()) { statement.executeQuery("SELECT id FROM user_management_guard WHERE id=1 FOR UPDATE").close(); }
            var pending=CompletableFuture.supplyAsync(()->{
                try { return wire.issue(UUID.randomUUID()); } catch(Exception error) { throw new CompletionException(error); }
            });
            // Wait for MySQL to prove the actual request is queued behind this guard; do not infer from delay.
            waitForLock(wire.jdbc);
            try(var statement=connection.createStatement()) { statement.executeUpdate("DELETE FROM role_permission WHERE role_id=(SELECT id FROM role WHERE code='WAREHOUSE_ADMIN') AND permission_id=(SELECT id FROM permission WHERE code='pda:independent')"); }
            connection.commit();assertThat(pending.get(45,TimeUnit.SECONDS).statusCode()).isEqualTo(403);
        }
        assertThat(wire.get(UUID.randomUUID().toString()).statusCode()).isEqualTo(403);
    }
    private static void waitForLock(JdbcTemplate jdbc) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(System.nanoTime()<deadline) {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits WHERE REQUESTING_ENGINE_LOCK_ID IN " +
                "(SELECT ENGINE_LOCK_ID FROM performance_schema.data_locks WHERE OBJECT_SCHEMA=DATABASE() AND OBJECT_NAME='user_management_guard')",Long.class)>0)return;
            Thread.sleep(50);
        }
        throw new AssertionError("No actual MySQL identity lock wait observed");
    }
}
