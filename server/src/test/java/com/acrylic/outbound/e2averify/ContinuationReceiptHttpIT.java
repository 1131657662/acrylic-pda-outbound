package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "B4_RETENTION_VERIFY", matches = "true")
class ContinuationReceiptHttpIT {
    @Test void originalHttpReceiptsSurviveActivationCompletionAndRejectedReplays() throws Exception {
        try (var fixture = new ContinuationReceiptHttpFixture()) {
            var commands = new ContinuationReceiptHttpCommands(fixture);
            var reopen = commands.request(null); var issued = fixture.accepted(reopen, "issued");
            assertThat(fixture.accepted(reopen, "issued-replay")).isEqualTo(issued);
            UUID ticket = UUID.fromString(SubmissionJson.parse(issued).path("businessDecision").path("ticketId").asText());
            var activation = commands.request(ticket);
            permissionDenied(fixture, activation);
            wrongTargetCannotConsumeFreshTicket(fixture, activation);
            rollbackAtFinalAudit(fixture, activation);
            var activated = fixture.accepted(activation, "activated");
            assertThat(fixture.accepted(activation, "activation-replay")).isEqualTo(activated);
            assertThat(fixture.accepted(reopen, "issued-after-activation")).isEqualTo(issued);
            rejectsChangedBodyAndIdentity(fixture, activation);
            assertOriginalSource(fixture, ticket);
            ContinuationIntakeGuardHttpChecks.verify(fixture, SubmissionJson.parse(activated), "activated");
            ContinuationIntakeGuardHttpChecks.ordinaryAuthorizationStillRecoversAnAbort(fixture);
            // Synthetic settlement isolates historical replay; it is not new-round intake/accounting proof.
            fixture.completed(SubmissionJson.parse(activated));
            assertThat(fixture.accepted(activation, "activation-after-completion")).isEqualTo(activated);
            assertThat(fixture.accepted(reopen, "issued-after-completion")).isEqualTo(issued);
            ContinuationIntakeGuardHttpChecks.verify(fixture, SubmissionJson.parse(activated), "completed");
            assertThat(fixture.database.jdbc.queryForObject("SELECT status FROM temporary_source_continuation WHERE id=?",
                String.class, Ids.bytes(ticket))).isEqualTo("COMPLETED");
            Files.writeString(fixture.database.profile.attempt.resolve("test-completed.marker"),
                "Synthetic nonempty source; actual HTTP REOPEN/ACTIVATE/replay, permission/identity/body denial, audit rollback; historical completed replay.\n");
        }
    }

    private void permissionDenied(ContinuationReceiptHttpFixture fixture, ObjectNode activation) throws Exception {
        var jdbc = fixture.database.jdbc; byte[] user = Ids.bytes(fixture.database.id("operatorId"));
        jdbc.update("DELETE ur FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.code='SUPERVISOR'", user);
        try {
            var response = fixture.post(activation, fixture.token, "permission-denied");
            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo("PERMISSION_DENIED");
        } finally {
            jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='SUPERVISOR'", user);
        }
    }

    private void wrongTargetCannotConsumeFreshTicket(ContinuationReceiptHttpFixture fixture, ObjectNode activation) throws Exception {
        var stock = fixture.database.stock();
        for (var entry : java.util.Map.of("wrong-person", fixture.otherToken, "wrong-device", fixture.otherDeviceToken).entrySet()) {
            var response = fixture.post(activation, entry.getValue(), entry.getKey());
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo("CONTINUATION_TARGET_IDENTITY_MISMATCH");
        }
        assertThat(fixture.database.stock()).isEqualTo(stock);
        assertThat(fixture.database.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation_activation", Integer.class)).isZero();
    }

    private void rollbackAtFinalAudit(ContinuationReceiptHttpFixture fixture, ObjectNode activation) throws Exception {
        var jdbc = fixture.database.jdbc;
        var before = jdbc.queryForObject("SELECT COUNT(*) FROM temporary_authorization", Integer.class);
        var stock = fixture.database.stock();
        jdbc.execute("CREATE TRIGGER continuation_http_fail BEFORE INSERT ON operation_event FOR EACH ROW "
            + "BEGIN IF NEW.event_type='TEMPORARY_ACTIVATE_CONTINUATION' THEN SIGNAL SQLSTATE '45000' "
            + "SET MESSAGE_TEXT='continuation HTTP final audit failure'; END IF; END");
        try { assertThat(fixture.post(activation, fixture.token, "audit-rollback").statusCode()).isEqualTo(500); }
        finally { jdbc.execute("DROP TRIGGER continuation_http_fail"); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_authorization", Integer.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation_activation", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM api_idempotency_request WHERE idempotency_key=?", Integer.class,
            activation.path("commandId").asText())).isZero();
        assertThat(fixture.database.stock()).isEqualTo(stock);
    }

    private void rejectsChangedBodyAndIdentity(ContinuationReceiptHttpFixture fixture, ObjectNode activation) throws Exception {
        var changed = activation.deepCopy().put("reason", "different text under same key");
        var mismatch = fixture.post(changed, fixture.token, "changed-body");
        assertThat(mismatch.statusCode()).isEqualTo(409);
        assertThat(SubmissionJson.parse(mismatch.body()).path("code").asText()).isEqualTo("IDEMPOTENCY_CONTENT_MISMATCH");
        var foreign = fixture.post(activation, fixture.otherToken, "other-identity");
        assertThat(foreign.statusCode()).isEqualTo(409);
        assertThat(SubmissionJson.parse(foreign.body()).path("code").asText()).isEqualTo("COMMAND_CONTEXT_CHANGED");
    }

    private void assertOriginalSource(ContinuationReceiptHttpFixture fixture, UUID ticket) {
        var jdbc = fixture.database.jdbc;
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation_activation", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE temporary_continuation_id=? AND state='TEMP_CONTINUATION_RESERVED'",
            Integer.class, Ids.bytes(ticket))).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT status FROM submission WHERE id=?", String.class,
            Ids.bytes(fixture.database.id("mappedSubmissionId")))).isEqualTo("ABORTED");
        assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(authorization_id) FROM temporary_work_origin WHERE creator_device_id=? AND client_order_id=?",
            String.class, Ids.bytes(fixture.database.id("deviceId")), Ids.bytes(fixture.database.id("mappedClientOrderId"))))
            .isEqualTo(fixture.database.seed.get("authorizationId"));
    }
}
