package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Confirmation through real recipient HTTP; no assignment, activation, owner transfer or accounting. */
public final class HandoverTemporaryConfirmationChecks {
    private final HandoverTemporaryConfirmationHttp http;
    private final HandoverTemporaryConfirmationSql facts;
    private ObjectNode command;
    private byte[] receipt;

    public HandoverTemporaryConfirmationChecks(WebApplicationContext app, HandoverTemporaryApprovalChecks approval) throws Exception {
        http = new HandoverTemporaryConfirmationHttp(new HandoverTemporaryTargetFixture(app, approval.targetContext()));
        facts = new HandoverTemporaryConfirmationSql(http);
    }

    public void verify() throws Exception {
        var f = http.f; f.acknowledge();
        var prepared = f.context.reviewer().command(f.command(), 200, null);
        var manifestBytes = f.get("/handovers/" + f.source.kase + "/manifest", f.token, 200, null);
        var manifest = SubmissionJson.parse(manifestBytes); var parsed = SubmissionJson.parse(prepared);
        command = SubmissionJson.object().put("commandVersion", 1).put("commandId", UUID.randomUUID().toString())
            .put("expectedCaseVersion", Long.toString(Math.addExact(Long.parseLong(parsed.path("caseContext").path("caseVersion").asText()), 1)))
            .put("targetAuthorizationId", f.authorization.path("authorizationId").asText())
            .put("preparedTicketSha256", manifest.path("preparedTicketSha256").asText()).put("physicalConfirmed", true)
            .put("payloadSha256", manifest.path("recoveryPackageSha256").asText()).put("checklistSha256", SubmissionJson.sha(manifest.path("checklist")));
        Files.write(http.directory.resolve("manifest.json"), manifestBytes);
        refusals(); mutations(); facts.rollback(command);
        var before = facts.snapshot("confirmation-before"); concurrent(); var after = facts.snapshot("confirmation-after");
        for (String table : before.keySet()) if (!Set.of("temporary_handover_confirmation", "operation_event").contains(table))
            assertThat(after.get(table)).as(table).isEqualTo(before.get(table));
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_handover_confirmation", Long.class)).isEqualTo(1);
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE event_type='HANDOVER_TEMP_TARGET_CONFIRMED'", Long.class)).isEqualTo(1);
        assertThat(http.submit(command, 200, null)).isEqualTo(receipt);
        http.submit(command.deepCopy().put("commandId", UUID.randomUUID().toString()), 409, "IDEMPOTENCY_CONTENT_MISMATCH");
        http.submit(command.deepCopy().put("payloadSha256", "b".repeat(64)), 409, "IDEMPOTENCY_CONTENT_MISMATCH");
        immutable(); Files.writeString(http.directory.resolve("verified.marker"), "Original confirmation is immutable; no stock, round, assignment or accounting change.\n");
    }

    public void recipientWithoutFormalAssignmentPermission() {
        var jdbc = http.f.source.jdbc; var role = UUID.randomUUID();
        jdbc.update("INSERT INTO role VALUES(?,?,?)", Ids.bytes(role), "D06_" + role, "TEMP recipient without FORMAL assignment");
        jdbc.update("INSERT INTO role_permission SELECT ?,p.id FROM permission p JOIN role_permission rp ON rp.permission_id=p.id "
            + "JOIN role r ON r.id=rp.role_id WHERE r.code='WAREHOUSE_ADMIN' AND p.code<>'pda:assign'", Ids.bytes(role));
        jdbc.update("DELETE FROM user_role WHERE user_id=? AND role_id=(SELECT id FROM role WHERE code='WAREHOUSE_ADMIN')", Ids.bytes(http.f.user));
        jdbc.update("INSERT INTO user_role VALUES(?,?)", Ids.bytes(http.f.user), Ids.bytes(role));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_role ur JOIN role_permission rp ON rp.role_id=ur.role_id "
            + "JOIN permission p ON p.id=rp.permission_id WHERE ur.user_id=? AND p.code='pda:assign'", Long.class, Ids.bytes(http.f.user))).isZero();
    }

    private void refusals() throws Exception {
        var before = facts.snapshot("refusals-before");
        http.submit(command.deepCopy().put("physicalConfirmed", false), 400, "INVALID_TEMP_HANDOVER_CONFIRMATION");
        http.submit(command.deepCopy().put("mayResumeScanning", true), 400, "INVALID_TEMP_HANDOVER_CONFIRMATION");
        http.submit(command.deepCopy().put("expectedCaseVersion", "999"), 409, "HANDOVER_CONFIRMATION_CHANGED");
        for (String field : List.of("preparedTicketSha256", "payloadSha256", "checklistSha256"))
            http.submit(command.deepCopy().put(field, "f".repeat(64)), 409, "HANDOVER_CONFIRMATION_CHANGED");
        http.send(new HandoverTemporaryConfirmationHttp.Request(command, http.f.source.token, "1", 404, "HANDOVER_CASE_NOT_FOUND"));
        http.send(new HandoverTemporaryConfirmationHttp.Request(command, http.f.token, "2", 409, "SERVER_RECOVERY_EPOCH_MISMATCH"));
        assertThat(facts.snapshot("refusals-after")).isEqualTo(before);
    }

    private void mutations() throws Exception {
        var jdbc = http.f.source.jdbc; String id = http.f.authorization.path("authorizationId").asText();
        jdbc.update("UPDATE temporary_authorization SET issued_at=issued_at-INTERVAL 7 DAY,expires_at=expires_at-INTERVAL 7 DAY WHERE id=UUID_TO_BIN(?)", id);
        try { http.submit(command, 409, "TEMPORARY_AUTH_EXPIRED"); }
        finally { jdbc.update("UPDATE temporary_authorization SET issued_at=issued_at+INTERVAL 7 DAY,expires_at=expires_at+INTERVAL 7 DAY WHERE id=UUID_TO_BIN(?)", id); }
        mutation("user_auth_version", 999L, "TEMPORARY_REAUTHORIZATION_REQUIRED");
        mutation("device_version", 999L, "TEMPORARY_REAUTHORIZATION_REQUIRED");
        mutation("recovery_epoch", 999L, "TEMPORARY_RECOVERY_REQUIRED");
        mutation("signed_token", "broken", "HANDOVER_TARGET_CREDENTIAL_INVALID");
        jdbc.update("UPDATE device SET temporary_order_enabled=false WHERE id=?", Ids.bytes(http.f.device));
        try { http.submit(command, 403, "TEMPORARY_ORDER_DISABLED"); }
        finally { jdbc.update("UPDATE device SET temporary_order_enabled=true WHERE id=?", Ids.bytes(http.f.device)); }
        jdbc.update("DELETE FROM user_role WHERE user_id=? AND role_id=(SELECT id FROM role WHERE code='TEMPORARY_ORDER_CREATOR')", Ids.bytes(http.f.user));
        try { http.submit(command, 403, "PERMISSION_DENIED"); }
        finally { jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='TEMPORARY_ORDER_CREATOR'", Ids.bytes(http.f.user)); }
    }

    private void mutation(String column, Object value, String code) throws Exception {
        var jdbc = http.f.source.jdbc; String id = http.f.authorization.path("authorizationId").asText();
        Object original = jdbc.queryForObject("SELECT " + column + " FROM temporary_authorization WHERE id=UUID_TO_BIN(?)", Object.class, id);
        jdbc.update("UPDATE temporary_authorization SET " + column + "=? WHERE id=UUID_TO_BIN(?)", value, id);
        var before = facts.snapshot("mutation-" + column + "-before");
        try { http.submit(command, 409, code); assertThat(facts.snapshot("mutation-" + column + "-after")).isEqualTo(before); }
        finally { jdbc.update("UPDATE temporary_authorization SET " + column + "=? WHERE id=UUID_TO_BIN(?)", original, id); }
    }

    private void concurrent() throws Exception {
        try (var connection = http.f.source.jdbc.getDataSource().getConnection(); var workers = Executors.newFixedThreadPool(2)) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) { statement.executeQuery("SELECT id FROM user_management_guard WHERE id=1 FOR UPDATE").close(); }
            var first = workers.submit(() -> http.submit(command, 200, null)); var second = workers.submit(() -> http.submit(command, 200, null));
            int waits = 0; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (waits < 2 && System.nanoTime() < deadline) {
                waits = http.f.source.jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits", Integer.class);
                if (waits < 2) Thread.sleep(50);
            }
            assertThat(waits).isGreaterThanOrEqualTo(2); connection.commit();
            receipt = first.get(30, TimeUnit.SECONDS); assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo(receipt);
        }
        Files.write(http.directory.resolve("confirmation-original.json"), receipt);
        Files.writeString(http.directory.resolve("confirmation-command.json"), SubmissionJson.canonical(command));
    }

    private void immutable() {
        var jdbc = http.f.source.jdbc;
        assertThatThrownBy(() -> jdbc.update("UPDATE temporary_handover_confirmation SET command_bytes=X'00'"))
            .hasMessageContaining("Temporary handover confirmation is immutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM temporary_handover_confirmation"))
            .hasMessageContaining("Temporary handover confirmation is retained");
    }

    public void restart() throws Exception { assertThat(http.submit(command, 200, null)).isEqualTo(receipt); }
    public void defaultClosed() throws Exception { http.submit(command, 404, null); }
    HandoverTemporaryTargetFixture fixture() { return http.f; }
    Map<String, List<String>> snapshot(String label) throws Exception { return facts.snapshot(label); }
}
