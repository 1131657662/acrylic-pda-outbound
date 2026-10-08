package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Actual HTTP consumes the named recipient's TEMP confirmation, never a FORMAL/R06 credential. */
public final class HandoverTemporaryActivationChecks {
    private final HandoverTemporaryConfirmationChecks confirmation;
    private final HandoverTemporaryTargetFixture f;
    private final Path directory;
    private final HandoverTemporaryActivationSql sql;
    private ObjectNode command;
    private byte[] receipt;
    private Map<String, List<String>> baseline;

    public HandoverTemporaryActivationChecks(HandoverTemporaryConfirmationChecks confirmation) throws Exception {
        this.confirmation = confirmation; this.f = confirmation.fixture();
        directory = Files.createDirectory(f.directory.resolve("activation")); sql = new HandoverTemporaryActivationSql(confirmation, directory);
    }

    public void verify() throws Exception {
        command = command();
        baseline = confirmation.snapshot("activation-baseline");
        var before = confirmation.snapshot("activation-refusals-before");
        f.context.reviewer().command(command.deepCopy().put("targetUserId", f.source.head.actor().toString()), 409, "HANDOVER_CONTEXT_CHANGED");
        f.context.reviewer().command(command.deepCopy().put("mayResumeScanning", true), 400, "INVALID_HANDOVER_COMMAND");
        assertThat(confirmation.snapshot("activation-refusals-after")).isEqualTo(before);
        sessionRefusal(); expiredAuthorization();
        sql.rollback(command, "order_sync_outbox"); sql.rollback(command, "operation_event");
        concurrent(); verifyEffects();
        assertThat(f.context.reviewer().command(command, 200, null)).isEqualTo(receipt);
        f.context.reviewer().command(command.deepCopy().put("reason", "改写原意图"), 409, "IDEMPOTENCY_CONTENT_MISMATCH");
        var next = command.deepCopy().put("commandId", UUID.randomUUID().toString());
        f.context.reviewer().command(next, 409, "HANDOVER_CONTEXT_CHANGED");
        Files.writeString(directory.resolve("verified.marker"), "Dedicated TEMP activation transfers retained owners and advances exactly once.\n");
    }

    private ObjectNode command() throws Exception {
        var view = SubmissionJson.parse(f.context.reviewer().get("/handovers/" + f.source.kase + "/context?warehouseId="
            + f.source.head.warehouse() + "&commandType=ACTIVATE"));
        var command = SubmissionJson.object().put("commandVersion", 1).put("commandType", "ACTIVATE")
            .put("commandId", UUID.randomUUID().toString()).put("warehouseId", f.source.head.warehouse().toString())
            .put("caseId", f.source.kase.toString()).put("transportRecoveryEpoch", "1").put("reason", "本人已确认原实物，激活接收轮次")
            .put("targetDeviceId", f.device.toString()).put("targetUserId", f.user.toString());
        command.set("expected", view.path("expected")); return command;
    }

    private void sessionRefusal() throws Exception {
        String session = f.session.path("sessionId").asText();
        f.source.jdbc.update("UPDATE pda_session SET revoked_at=UTC_TIMESTAMP(3) WHERE id=UUID_TO_BIN(?)", session);
        var before = confirmation.snapshot("activation-session-before");
        try { f.context.reviewer().command(command, 401, "AUTH_EXPIRED");
            assertThat(confirmation.snapshot("activation-session-after")).isEqualTo(before); }
        finally { f.source.jdbc.update("UPDATE pda_session SET revoked_at=NULL WHERE id=UUID_TO_BIN(?)", session); }
    }

    private void expiredAuthorization() throws Exception {
        String id = f.authorization.path("authorizationId").asText();
        f.source.jdbc.update("UPDATE temporary_authorization SET issued_at=issued_at-INTERVAL 7 DAY,expires_at=expires_at-INTERVAL 7 DAY WHERE id=UUID_TO_BIN(?)", id);
        var before = confirmation.snapshot("activation-expired-before");
        try { f.context.reviewer().command(command, 409, "TEMPORARY_AUTH_EXPIRED");
            assertThat(confirmation.snapshot("activation-expired-after")).isEqualTo(before); }
        finally { f.source.jdbc.update("UPDATE temporary_authorization SET issued_at=issued_at+INTERVAL 7 DAY,expires_at=expires_at+INTERVAL 7 DAY WHERE id=UUID_TO_BIN(?)", id); }
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
        Files.write(directory.resolve("activation-original.json"), receipt);
        Files.writeString(directory.resolve("activation-command.json"), SubmissionJson.canonical(command));
    }

    private void verifyEffects() throws Exception {
        var jdbc = f.source.jdbc; var decision = SubmissionJson.parse(receipt).path("businessDecision");
        assertThat(decision.path("kind").asText()).isEqualTo("TEMP_HANDOVER_ACTIVATE");
        assertThat(decision.path("toWorkRound").asText()).isEqualTo("2");
        var newManifest = SubmissionJson.parse(decision.path("authorization").path("manifest").asText());
        assertThat(java.time.Instant.parse(newManifest.path("expiresAt").asText()))
            .isBeforeOrEqualTo(java.time.Instant.parse(f.manifest.path("expiresAt").asText()));
        for (String table : List.of("temporary_handover_activation_ticket", "temporary_handover_activation"))
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)).as(table).isEqualTo(1);
        long retained = jdbc.queryForObject("SELECT COUNT(*) FROM temporary_handover_activation_owner", Long.class);
        assertThat(retained).isGreaterThan(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE state='TEMP_HANDOVER_RESERVED' AND temporary_handover_activation_id IS NOT NULL", Long.class)).isEqualTo(retained);
        assertThat(jdbc.queryForObject("SELECT work_round FROM submission_scope WHERE id=UUID_TO_BIN(?)", Long.class,
            SubmissionJson.parse(receipt).path("caseContext").path("scopeId").asText())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT open_flag FROM handover_case WHERE id=?", Object.class, Ids.bytes(f.source.kase))).isNull();
        assertThat(jdbc.queryForObject("SELECT current_submission_id FROM submission_scope WHERE id=UUID_TO_BIN(?)", byte[].class,
            SubmissionJson.parse(receipt).path("caseContext").path("scopeId").asText())).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_assignment WHERE device_id=?", Long.class, Ids.bytes(f.device))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation_activation", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE event_type='HANDOVER_ACTIVATE'", Long.class)).isEqualTo(1);
        var outbox = jdbc.queryForObject("SELECT payload FROM order_sync_outbox WHERE event_type='HANDOVER_TEMP_ACTIVATED'", String.class);
        assertThat(outbox).doesNotContain("offlineToken", "signedManifest", "targetSessionId");
        assertThat(f.get("/handovers/" + f.source.kase + "/manifest", f.token, 200, null)).isNotEmpty();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM temporary_handover_activation")).hasMessageContaining("TEMP handover activations are retained");
        verifyUnchangedBusiness();
        sync();
    }

    private void sync() throws Exception {
        var response = f.get("/sync/orders", f.source.token, 200, null);
        Files.write(directory.resolve("sync-after-activation.json"), response);
        var row = f.source.jdbc.queryForMap("""
            SELECT o.status,p.status projected,x.published_seq FROM outbound_order o
            JOIN sync_order_projection p ON p.order_id=o.id AND p.warehouse_id=o.warehouse_id AND p.recovery_epoch=1
            JOIN order_sync_outbox x ON x.order_id=o.id AND x.event_type='HANDOVER_TEMP_ACTIVATED'
            WHERE o.id=? AND JSON_UNQUOTE(JSON_EXTRACT(x.payload,'$.caseId'))=?
            """, Ids.bytes(f.source.head.order()), f.source.kase.toString());
        assertThat(row.get("status")).isEqualTo("CONTINUING");
        assertThat(row.get("projected")).isEqualTo(row.get("status"));
        assertThat(row.get("published_seq")).isNotNull();
        Files.writeString(directory.resolve("sync-status.json"), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(row));
    }

    private void verifyUnchangedBusiness() throws Exception {
        var after = confirmation.snapshot("activation-completed");
        for (String table : List.of("inventory_position", "inventory_ledger", "order_result_version", "order_assignment",
                "submitted_capture", "submitted_event", "source_evidence_set", "source_evidence_item", "source_evidence_verification",
                "source_evidence_intake_receipt", "temporary_handover_confirmation", "temporary_source_continuation_activation"))
            assertThat(after.get(table)).as(table).isEqualTo(baseline.get(table));
    }

    public void restart() throws Exception {
        assertThat(f.context.reviewer().command(command, 200, null)).isEqualTo(receipt);
    }

    public void defaultClosed() throws Exception {
        f.context.reviewer().command(command.deepCopy().put("commandId", UUID.randomUUID().toString()), 404, null);
    }
}
