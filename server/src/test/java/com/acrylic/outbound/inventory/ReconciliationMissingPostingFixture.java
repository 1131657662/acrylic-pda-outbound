package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Real separated sessions and controlled bytes on one synthetic confirmed missing-debit dispatch. */
final class ReconciliationMissingPostingFixture {
    final CorrectionAccountingHttpFixture original;
    final CorrectionAttachmentHttp maker, checker;
    final Path directory;
    final JsonNode attachment;
    String caseId;
    ReconciliationMissingPostingFixture(WebApplicationContext context, Path attempt, String name) throws Exception {
        original = new CorrectionAccountingHttpFixture(context, attempt, false, "reconcile-" + name);
        directory = original.evidence;
        maker = new CorrectionAttachmentHttp(original.makerIdentity.user().username(), directory.resolve("maker-http.log"));
        String supervisor = original.jdbc.queryForObject("SELECT username FROM app_user u JOIN user_role ur ON ur.user_id=u.id"
            + " JOIN role r ON r.id=ur.role_id JOIN user_warehouse uw ON uw.user_id=u.id"
            + " WHERE r.code='SUPERVISOR' AND uw.warehouse_id=UUID_TO_BIN(?) ORDER BY u.id DESC LIMIT 1", String.class, original.warehouse);
        checker = new CorrectionAttachmentHttp(supervisor, directory.resolve("checker-http.log"));
        byte[] bytes = ("ISOLATED physical dispatch receipt " + original.dispatch).getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("physical-original.bin"), bytes);
        attachment = maker.upload(original.warehouse, UUID.randomUUID().toString(), bytes, CorrectionAttachmentHttp.sha(bytes), 200, false).path("data");
    }

    ObjectNode command(String kind, boolean review) throws Exception {
        var response = SubmissionJson.parse((review ? checker : maker).get(contextPath(kind), 200));
        var body = base(kind);
        body.set("expected", response.path("expected"));
        if (kind.equals("RECONCILE_DISCOVER")) body.put("dispatchId", original.dispatch);
        else body.put("caseId", caseId);
        if (kind.equals("RECONCILE_SUBMIT")) body.set("evidence", evidence());
        return body;
    }

    ObjectNode base(String kind) {
        return SubmissionJson.object().put("commandVersion", 1).put("commandType", kind).put("commandId", UUID.randomUUID().toString())
            .put("warehouseId", original.warehouse).put("transportRecoveryEpoch", "1").put("reason", "ISOLATED missing-debit reconciliation");
    }

    private String contextPath(String kind) {
        return "/corrections/reconciliation/context?warehouseId=" + original.warehouse + "&commandType=" + kind
            + (kind.equals("RECONCILE_DISCOVER") ? "&dispatchId=" + original.dispatch : "&caseId=" + caseId);
    }

    ObjectNode evidence() {
        var evidence = SubmissionJson.object().put("observedAt", Instant.now().minusSeconds(10).toString())
            .put("place", "ISOLATED dispatch bay").put("observation", "DISPATCHED_CONFIRMED").put("reference", "ISOLATED actual receipt");
        evidence.putArray("attachments").add(attachment.deepCopy()); return evidence;
    }

    byte[] signed(CorrectionAttachmentHttp actor, ObjectNode body) throws Exception {
        byte[] bytes = actor.command(body, 200); SupervisorCorrectionReceipt.verify(bytes, original.publicKey);
        Files.writeString(directory.resolve(body.path("commandId").asText() + "-command.json"), SubmissionJson.canonical(body));
        Files.write(directory.resolve(body.path("commandId").asText() + "-receipt.json"), bytes);
        assertThat(actor.command(body, 200)).containsExactly(bytes); return bytes;
    }

    void discover() throws Exception {
        caseId = SubmissionJson.parse(signed(maker, command("RECONCILE_DISCOVER", false))).at("/businessDecision/caseId").asText();
    }
    void submit() throws Exception { signed(maker, command("RECONCILE_SUBMIT", false)); }

    JsonNode state() {
        var state = SubmissionJson.object(); state.set("result", original.result()); state.put("postings", original.postings());
        state.put("caseStatus", original.jdbc.queryForObject("SELECT status FROM inventory_reconciliation WHERE id=UUID_TO_BIN(?)", String.class, caseId));
        for (String table : new String[]{"correction_command_receipt", "order_result_version", "order_sync_outbox", "correction_effect"})
            state.put(table, original.jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE order_id=UUID_TO_BIN(?)", Integer.class, original.order));
        state.set("stock", stock()); return state;
    }

    JsonNode stock() {
        return SubmissionJson.parse(original.jdbc.queryForObject("SELECT JSON_OBJECT('cycle',b.dispatch_cycle,'bookStatus',p.book_status,"
            + "'blockedReason',p.blocked_reason,'version',p.row_version,'upstreamPresent',p.upstream_present) FROM bale b"
            + " JOIN inventory_position p ON p.bale_id=b.id WHERE b.id=UUID_TO_BIN(?)", String.class, original.facts(original.dispatch).path("baleId").asText()));
    }
}
