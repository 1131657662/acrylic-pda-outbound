package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real HTTP acceptance checks for receiving-machine continuation and its immutable evidence. */
public final class HandoverContinuationChecks {
    public void verify(WebApplicationContext app, java.nio.file.Path attempt) throws Exception {
        var f = new HandoverContinuationFixture(app, attempt);
        rejectBeforeDownloadAck(f);
        f.acknowledge();
        rejectWrongIdentity(f);
        rejectClaimDrift(f);
        rejectStoredRecoveryDrift(f);
        rejectAndRollbackWriteFailure(f);
        var receipt = concurrentFirstWrite(f);
        assertProjection(f, receipt);
        new HandoverContinuationSubmissionChecks().verify(f, SubmissionJson.parse(receipt));
        assertReplayAndConflict(f, receipt);
    }

    private void rejectBeforeDownloadAck(HandoverContinuationFixture f) throws Exception {
        assertCode(f.resume(f.command, f.tokens, 409), "HANDOVER_DOWNLOAD_NOT_ACKED");
        assertCode(f.resume(f.command.deepCopy().put("unexpected", true), f.tokens, 409), "INVALID_HANDOVER_CONTINUATION");
    }

    private void rejectWrongIdentity(HandoverContinuationFixture f) throws Exception {
        assertCode(f.resume(f.command, f.source.r.f.tokens, 404), "HANDOVER_CASE_NOT_FOUND");
        var wrongAssignment = f.command.deepCopy().put("assignmentId", UUID.randomUUID().toString());
        assertCode(f.resume(wrongAssignment, f.tokens, 409), "HANDOVER_ASSIGNMENT_MISMATCH");
    }

    private void rejectClaimDrift(HandoverContinuationFixture f) throws Exception {
        var zero = "0".repeat(64);
        assertCode(f.resume(f.command.deepCopy().put("payloadSha256", zero), f.tokens, 409), "HANDOVER_RECOVERY_CHANGED");
        assertCode(f.resume(f.command.deepCopy().put("retainedManifestSha256", zero), f.tokens, 409), "HANDOVER_RETAINED_SET_CHANGED");
        var high = Long.parseLong(f.command.path("nextOrderScanSeq").asText()) + 1;
        assertCode(f.resume(f.command.deepCopy().put("nextOrderScanSeq", Long.toString(high)), f.tokens, 409), "HANDOVER_HIGH_WATERMARK_CHANGED");
        assertCode(f.resume(f.command.deepCopy().put("planSha256", zero), f.tokens, 409), "HANDOVER_PLAN_CHANGED");
    }

    private void rejectStoredRecoveryDrift(HandoverContinuationFixture f) throws Exception {
        var jdbc = f.source.r.f.jdbc;
        var request = Ids.bytes(UUID.fromString(f.source.r.recall.path("changeRequestId").asText()));
        var original = jdbc.queryForObject("SELECT payload_sha256 FROM recovery_package WHERE request_id=?", byte[].class, request);
        try {
            jdbc.update("UPDATE recovery_package SET payload_sha256=? WHERE request_id=?", new byte[32], request);
            assertCode(f.resume(f.command, f.tokens, 409), "HANDOVER_EVIDENCE_CHANGED");
        } finally {
            jdbc.update("UPDATE recovery_package SET payload_sha256=? WHERE request_id=?", original, request);
        }
    }

    private void rejectAndRollbackWriteFailure(HandoverContinuationFixture f) throws Exception {
        var jdbc = f.source.r.f.jdbc;
        var before = state(f);
        jdbc.execute("CREATE TRIGGER e2a_handover_continuation_write_failure BEFORE INSERT ON handover_continuation FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='continuation evidence failure'");
        try { f.resume(f.command, f.tokens, 500); }
        finally { jdbc.execute("DROP TRIGGER e2a_handover_continuation_write_failure"); }
        assertThat(state(f)).isEqualTo(before);
    }

    private byte[] concurrentFirstWrite(HandoverContinuationFixture f) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            Callable<byte[]> call = () -> { gate.await(30, TimeUnit.SECONDS); return f.resume(f.command, f.tokens, 200); };
            var first = pool.submit(call); var second = pool.submit(call); gate.countDown();
            var left = first.get(40, TimeUnit.SECONDS); var right = second.get(40, TimeUnit.SECONDS);
            assertThat(left).isEqualTo(right);
            return left;
        } finally { pool.shutdownNow(); }
    }

    private void assertProjection(HandoverContinuationFixture f, byte[] bytes) throws Exception {
        var receipt = SubmissionJson.parse(bytes); var jdbc = f.source.r.f.jdbc;
        assertThat(receipt.path("status").asText()).isEqualTo("ACTIVATED");
        assertThat(receipt.path("scope").asText()).isEqualTo("pda:scan");
        assertThat(receipt.path("stockReleased").asBoolean(true)).isFalse();
        assertThat(receipt.path("signedReceipt").asText()).isNotBlank();
        assertThat(receipt.path("assignment").path("id").asText()).isEqualTo(f.assignment.path("id").asText());
        assertThat(receipt.path("authorizationId").asText()).isNotEqualTo(receipt.path("previousAuthorizationId").asText());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM handover_continuation WHERE case_id=UUID_TO_BIN(?)", Integer.class, f.source.kase.toString())).isEqualTo(1);
        var continuation = jdbc.queryForMap("SELECT BIN_TO_UUID(request_id) request_id,BIN_TO_UUID(previous_submission_id) previous_submission_id,BIN_TO_UUID(recovery_package_id) recovery_package_id,status,BIN_TO_UUID(new_assignment_id) assignment_id FROM order_continuation WHERE id=UUID_TO_BIN(?)", receipt.path("continuationId").asText());
        assertThat(continuation.get("request_id").toString()).isEqualTo(f.source.r.recall.path("changeRequestId").asText());
        assertThat(continuation.get("previous_submission_id").toString()).isEqualTo(f.source.r.f.id());
        assertThat(continuation.get("recovery_package_id").toString()).isEqualTo(f.source.r.body.path("recoveryPackageId").asText());
        assertThat(continuation.get("assignment_id").toString()).isEqualTo(f.assignment.path("id").asText());
        assertThat(continuation.get("status").toString()).isEqualTo("ACTIVATED");
        assertThat(jdbc.queryForObject("SELECT phase FROM order_submission_slot WHERE order_id=UUID_TO_BIN(?)", String.class, f.assignment.path("orderId").asText())).isEqualTo("CONTINUING");
        assertThat(jdbc.queryForObject("SELECT current_submission_id IS NULL FROM order_submission_slot WHERE order_id=UUID_TO_BIN(?)", Boolean.class, f.assignment.path("orderId").asText())).isTrue();
        assertThat(jdbc.queryForObject("SELECT work_round FROM submission_scope WHERE id=(SELECT scope_id FROM submission WHERE id=UUID_TO_BIN(?))", Integer.class, f.source.r.f.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE continuation_id=UUID_TO_BIN(?) AND state='CONTINUATION_RESERVED'", Integer.class, receipt.path("continuationId").asText())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger l JOIN dispatch_record d ON d.id=l.dispatch_id WHERE d.order_id=UUID_TO_BIN(?)", Integer.class, f.assignment.path("orderId").asText())).isZero();
        assertAuthorization(f, receipt);
        assertThat(jdbc.queryForObject("SELECT superseded_by IS NOT NULL FROM submission WHERE id=UUID_TO_BIN(?)", Boolean.class, f.source.r.f.id())).isTrue();
        assertThat(Files.exists(f.source.directory.getParent().resolve("storage/submissions/" + SubmissionJson.sha(f.source.r.body) + ".json"))).isTrue();
        var commandDigest = jdbc.queryForObject("SELECT command_sha256 FROM handover_continuation WHERE command_id=UUID_TO_BIN(?)", byte[].class, f.command.path("commandId").asText());
        assertThat(HexFormat.of().formatHex(commandDigest)).isEqualTo(SubmissionJson.sha(f.command));
        assertThat(SubmissionJson.parse(jdbc.queryForObject("SELECT command_text FROM handover_continuation WHERE case_id=UUID_TO_BIN(?)", String.class, f.source.kase.toString()))).isEqualTo(f.command);
        assertThatThrownBy(() -> jdbc.update("UPDATE handover_continuation SET receipt_text=receipt_text WHERE case_id=UUID_TO_BIN(?)", f.source.kase.toString())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM handover_continuation WHERE case_id=UUID_TO_BIN(?)", f.source.kase.toString())).isInstanceOf(DataAccessException.class);
    }

    private void assertAuthorization(HandoverContinuationFixture f, com.fasterxml.jackson.databind.JsonNode receipt) {
        var jdbc = f.source.r.f.jdbc;
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM offline_authorization WHERE assignment_id=UUID_TO_BIN(?)", Integer.class, f.assignment.path("id").asText())).isEqualTo(2);
        assertThat(jwtScope(jdbc.queryForObject("SELECT signed_token FROM offline_authorization WHERE id=UUID_TO_BIN(?)", String.class, f.assignment.path("offlineAuthorizationId").asText()))).isEqualTo("pda:handover-import");
        assertThat(jwtScope(receipt.path("signedToken").asText())).isEqualTo("pda:scan");
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL AND replaced_by=UUID_TO_BIN(?) FROM offline_authorization WHERE id=UUID_TO_BIN(?)", Boolean.class,
            receipt.path("authorizationId").asText(), receipt.path("previousAuthorizationId").asText())).isTrue();
    }

    private void assertReplayAndConflict(HandoverContinuationFixture f, byte[] receipt) throws Exception {
        assertThat(f.resume(f.command, f.tokens, 200)).isEqualTo(receipt);
        assertCode(f.resume(f.command, f.source.r.f.tokens, 404), "HANDOVER_CASE_NOT_FOUND");
        var changed = f.command.deepCopy().put("planSha256", "0".repeat(64));
        assertCode(f.resume(changed, f.tokens, 409), "IDEMPOTENCY_CONTENT_MISMATCH");
    }

    private static void assertCode(byte[] body, String expected) {
        assertThat(SubmissionJson.parse(body).path("code").asText()).isEqualTo(expected);
    }

    private static ObjectNode state(HandoverContinuationFixture f) {
        var jdbc = f.source.r.f.jdbc; var result = SubmissionJson.object();
        result.put("handover", count(jdbc, "SELECT COUNT(*) FROM handover_continuation WHERE case_id=?", Ids.bytes(f.source.kase)));
        result.put("continuation", count(jdbc, "SELECT COUNT(*) FROM order_continuation WHERE order_id=?", Ids.bytes(UUID.fromString(f.assignment.path("orderId").asText()))));
        result.put("authorization", count(jdbc, "SELECT COUNT(*) FROM offline_authorization WHERE assignment_id=?", Ids.bytes(UUID.fromString(f.assignment.path("id").asText()))));
        result.put("superseded", jdbc.queryForObject("SELECT superseded_by IS NOT NULL FROM submission WHERE id=?", Boolean.class, Ids.bytes(UUID.fromString(f.source.r.f.id()))));
        result.put("slot", jdbc.queryForObject("SELECT CONCAT(phase,':',current_submission_id IS NULL) FROM order_submission_slot WHERE order_id=?", String.class, Ids.bytes(UUID.fromString(f.assignment.path("orderId").asText()))));
        result.put("scopeRound", jdbc.queryForObject("SELECT work_round FROM submission_scope WHERE id=(SELECT scope_id FROM submission WHERE id=?)", Integer.class, Ids.bytes(UUID.fromString(f.source.r.f.id()))));
        result.put("bales", jdbc.queryForList("SELECT state,HEX(continuation_id) continuation_id,HEX(submission_id) submission_id,HEX(recovery_package_id) recovery_package_id FROM bale_commitment WHERE order_id=? ORDER BY bale_id", Ids.bytes(UUID.fromString(f.assignment.path("orderId").asText()))).toString());
        return result;
    }

    private static int count(JdbcTemplate jdbc, String sql, byte[] id) { return jdbc.queryForObject(sql, Integer.class, id); }

    private static String jwtScope(String token) {
        var parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        return SubmissionJson.parse(Base64.getUrlDecoder().decode(parts[1])).path("scope").asText();
    }
}
