package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** TEMP recipient preparation through real HTTP, with original-byte download and transactional failure evidence. */
public final class HandoverTemporaryTargetChecks {
    private final HandoverTemporaryTargetFixture f;
    private final TemporaryDispositionSqlChecks facts;
    private ObjectNode command;
    private byte[] receipt, manifest, body;

    public HandoverTemporaryTargetChecks(WebApplicationContext app, HandoverTemporaryApprovalChecks approval) throws Exception {
        f = new HandoverTemporaryTargetFixture(app, approval.targetContext());
        facts = new TemporaryDispositionSqlChecks(f.source.jdbc, f.directory.resolve("sql"));
    }

    public void verify(WebApplicationContext app) throws Exception {
        command = f.command(); refusal(command, 409, "HANDOVER_TARGET_CREDENTIAL_INVALID", "unacknowledged");
        f.acknowledge();
        var missing = command.deepCopy(); missing.remove("targetAuthorizationId");
        refusal(missing, 400, "HANDOVER_TARGET_AUTHORIZATION_REQUIRED", "missing-id");
        refusal(command.deepCopy().put("unknownField", true), 400, "INVALID_HANDOVER_COMMAND", "unknown-field");
        refusal(command.deepCopy().put("targetAuthorizationId", UUID.randomUUID().toString()), 404, "TEMPORARY_PREPARATION_NOT_FOUND", "unknown-id");
        refusal(command.deepCopy().put("targetDeviceId", f.source.head.control().device().toString()), 404, "TEMPORARY_PREPARATION_NOT_FOUND", "wrong-device");
        refusal(command.deepCopy().put("targetUserId", f.source.head.actor().toString()), 404, "TEMPORARY_PREPARATION_NOT_FOUND", "wrong-user");
        mutations(); rollback();
        var before = facts.snapshot("prepare-before"); concurrent();
        var after = facts.snapshot("prepare-after");
        for (String table : before.keySet()) if (!Set.of("api_idempotency_request", "operation_event").contains(table))
            assertThat(after.get(table)).as(table).isEqualTo(before.get(table));
        assertThat(f.context.reviewer().command(command, 200, null)).isEqualTo(receipt);
        f.context.reviewer().command(command.deepCopy().put("targetAuthorizationId", UUID.randomUUID().toString()), 409, "IDEMPOTENCY_CONTENT_MISMATCH");
        downloads(app); new HandoverTemporaryTargetReadChecks(f).verify();
    }

    private void mutations() throws Exception {
        expiry();
        authorization("user_auth_version", 99L, "TEMPORARY_REAUTHORIZATION_REQUIRED");
        authorization("device_version", 99L, "TEMPORARY_REAUTHORIZATION_REQUIRED");
        authorization("recovery_epoch", 99L, "TEMPORARY_RECOVERY_REQUIRED");
        authorization("key_id", "unknown", "HANDOVER_TARGET_CREDENTIAL_INVALID");
        authorization("signed_token", "invalid", "HANDOVER_TARGET_CREDENTIAL_INVALID");
        authorization("signed_manifest", "invalid", "HANDOVER_TARGET_CREDENTIAL_INVALID");
        authorization("mapping_revision", 999L, "HANDOVER_TARGET_CREDENTIAL_INVALID");
        f.source.jdbc.update("UPDATE device SET temporary_order_enabled=false WHERE id=?", Ids.bytes(f.device));
        try { refusal(command, 409, "HANDOVER_TARGET_TEMP_DISABLED", "admission"); }
        finally { f.source.jdbc.update("UPDATE device SET temporary_order_enabled=true WHERE id=?", Ids.bytes(f.device)); }
        f.source.jdbc.update("DELETE FROM user_role WHERE user_id=? AND role_id=(SELECT id FROM role WHERE code='TEMPORARY_ORDER_CREATOR')", Ids.bytes(f.user));
        try { refusal(command, 409, "HANDOVER_TARGET_TEMP_DISABLED", "permission"); }
        finally { f.source.jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='TEMPORARY_ORDER_CREATOR'", Ids.bytes(f.user)); }
    }

    private void expiry() throws Exception {
        String id = f.authorization.path("authorizationId").asText();
        f.source.jdbc.update("UPDATE temporary_authorization SET issued_at=issued_at-INTERVAL 7 DAY,expires_at=expires_at-INTERVAL 7 DAY WHERE id=UUID_TO_BIN(?)", id);
        try { refusal(command, 409, "TEMPORARY_AUTH_EXPIRED", "expiry"); }
        finally { f.source.jdbc.update("UPDATE temporary_authorization SET issued_at=issued_at+INTERVAL 7 DAY,expires_at=expires_at+INTERVAL 7 DAY WHERE id=UUID_TO_BIN(?)", id); }
    }

    private void authorization(String column, Object value, String code) throws Exception {
        String id = f.authorization.path("authorizationId").asText();
        Object original = f.source.jdbc.queryForObject("SELECT " + column + " FROM temporary_authorization WHERE id=UUID_TO_BIN(?)", Object.class, id);
        f.source.jdbc.update("UPDATE temporary_authorization SET " + column + "=? WHERE id=UUID_TO_BIN(?)", value, id);
        try { refusal(command, 409, code, column); }
        finally { f.source.jdbc.update("UPDATE temporary_authorization SET " + column + "=? WHERE id=UUID_TO_BIN(?)", original, id); }
    }

    private void rollback() throws Exception {
        var before = facts.snapshot("rollback-before");
        f.source.jdbc.execute("CREATE TRIGGER d06_temp_prepare_failure AFTER INSERT ON operation_event FOR EACH ROW "
            + "BEGIN IF NEW.event_type='HANDOVER_PREPARE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='D06_TEMP_PREPARE_LAST_WRITE'; END IF; END");
        try { f.context.reviewer().command(command, 500, "INTERNAL_ERROR"); }
        finally { f.source.jdbc.execute("DROP TRIGGER d06_temp_prepare_failure"); }
        assertThat(facts.snapshot("rollback-after")).isEqualTo(before);
        assertThat(f.source.jdbc.queryForObject("SELECT status FROM handover_case WHERE id=?", String.class, Ids.bytes(f.source.kase))).isEqualTo("APPROVED");
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM handover_command_receipt WHERE case_id=? AND command_type='PREPARE'",
            Integer.class, Ids.bytes(f.source.kase))).isZero();
    }

    private void concurrent() throws Exception {
        try (var connection = f.source.jdbc.getDataSource().getConnection(); var workers = Executors.newFixedThreadPool(2)) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) { statement.executeQuery("SELECT id FROM user_management_guard WHERE id=1 FOR UPDATE").close(); }
            var first = workers.submit(() -> f.context.reviewer().command(command, 200, null));
            var second = workers.submit(() -> f.context.reviewer().command(command, 200, null));
            int waits = 0; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (waits < 2 && System.nanoTime() < deadline) {
                waits = f.source.jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits", Integer.class);
                if (waits < 2) Thread.sleep(50);
            }
            assertThat(waits).isGreaterThanOrEqualTo(2); connection.commit();
            receipt = first.get(30, TimeUnit.SECONDS); assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo(receipt);
        }
        Files.write(f.directory.resolve("prepared-original.json"), receipt);
        Files.writeString(f.directory.resolve("prepare-command.json"), SubmissionJson.canonical(command));
    }

    private void downloads(WebApplicationContext app) throws Exception {
        String route = "/handovers/" + f.source.kase;
        manifest = f.get(route + "/manifest", f.token, 200, null); var value = SubmissionJson.parse(manifest);
        assertThat(value.path("sourceKind").asText()).isEqualTo("TEMP");
        assertThat(value.path("packageKind").asText()).isEqualTo("TEMP_FROZEN_PAYLOAD");
        var ticket = value.path("preparedTicket"); var qualification = ticket.path("temporaryQualification");
        assertThat(SubmissionJson.sha(ticket)).isEqualTo(value.path("preparedTicketSha256").asText());
        assertThat(qualification.path("authorizationId")).isEqualTo(f.authorization.path("authorizationId"));
        assertThat(qualification.path("expiresAt")).isEqualTo(f.manifest.path("expiresAt"));
        assertThat(qualification.path("planSha256")).isEqualTo(f.context.body().path("planSha256"));
        body = f.get(route + "/recovery-package", f.token, 200, null);
        String hash = f.source.jdbc.queryForObject("SELECT payload_sha256 FROM submission_payload WHERE submission_id=?", String.class,
            Ids.bytes(f.source.head.control().id()));
        assertThat(body).isEqualTo(app.getBean(SubmissionFiles.class).read(hash));
        assertThat(value.path("recoveryPackageSha256").asText()).isEqualTo(hash);
        assertThat(value.path("capabilities").size()).isEqualTo(5);
        for (var capability : value.path("capabilities")) assertThat(capability.isBoolean() && !capability.booleanValue()).isTrue();
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM order_assignment WHERE device_id=?", Long.class, Ids.bytes(f.device))).isZero();
        f.get(route + "/manifest", f.source.token, 404, "HANDOVER_CASE_NOT_FOUND");
        f.get(route + "/recovery-package", f.source.otherDeviceToken, 404, "HANDOVER_CASE_NOT_FOUND");
        Files.write(f.directory.resolve("manifest-original.json"), manifest); Files.write(f.directory.resolve("payload-original.json"), body);
    }

    private void refusal(ObjectNode candidate, int status, String code, String label) throws Exception {
        var before = facts.snapshot(label + "-before"); f.context.reviewer().command(candidate, status, code);
        assertThat(facts.snapshot(label + "-after")).isEqualTo(before);
    }

    public void restart() throws Exception {
        assertThat(new HandoverApprovalHttp(f.context.username(), f.directory).command(command, 200, null)).isEqualTo(receipt);
        assertThat(f.get("/handovers/" + f.source.kase + "/manifest", f.token, 200, null)).isEqualTo(manifest);
        assertThat(f.get("/handovers/" + f.source.kase + "/recovery-package", f.token, 200, null)).isEqualTo(body);
    }
}
