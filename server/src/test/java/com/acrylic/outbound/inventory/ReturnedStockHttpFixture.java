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

/** Actual HTTP return plus separately recorded physical evidence; synthetic isolation identities are explicit. */
final class ReturnedStockHttpFixture {
    final CorrectionAccountingHttpFixture original;
    final CorrectionAttachmentHttp maker, checker;
    final JsonNode attachment;
    final String document, bale;
    String caseId;
    ReturnedStockHttpFixture(WebApplicationContext context, Path attempt, String name) throws Exception {
        original = new CorrectionAccountingHttpFixture(context, attempt, true, "returned-" + name);
        bale = original.facts(original.dispatch).path("baleId").asText();
        maker = new CorrectionAttachmentHttp(original.makerIdentity.user().username(), original.evidence.resolve("maker-http.log"));
        String supervisor = original.jdbc.queryForObject("SELECT username FROM app_user u JOIN user_role ur ON ur.user_id=u.id"
            + " JOIN role r ON r.id=ur.role_id JOIN user_warehouse uw ON uw.user_id=u.id"
            + " WHERE r.code='SUPERVISOR' AND uw.warehouse_id=UUID_TO_BIN(?) ORDER BY u.id DESC LIMIT 1", String.class, original.warehouse);
        checker = new CorrectionAttachmentHttp(supervisor, original.evidence.resolve("checker-http.log"));
        byte[] bytes = ("ISOLATED returned physical evidence " + original.dispatch).getBytes(StandardCharsets.UTF_8);
        Files.write(original.evidence.resolve("physical-original.bin"), bytes);
        attachment = maker.upload(original.warehouse, UUID.randomUUID().toString(), bytes, CorrectionAttachmentHttp.sha(bytes), 200, false).path("data");
        var create = original.physicalReturn(0);
        var line = (ObjectNode) create.path("lines").get(0);
        line.put("evidenceId", attachment.path("attachmentId").asText()).put("evidenceSha256", attachment.path("evidenceSha256").asText());
        line.putArray("attachments").add(attachment.deepCopy());
        document = SubmissionJson.parse(signed(maker, create)).path("documentId").asText();
        signed(checker, original.command(document, "APPROVE"));
    }

    ObjectNode command(String suffix, boolean review) throws Exception {
        String kind = "RECONCILE_RETURN_" + suffix;
        String path = "/corrections/reconciliation/context?warehouseId=" + original.warehouse + "&commandType=" + kind
            + (suffix.equals("DISCOVER") ? "&dispatchId=" + original.dispatch : "&caseId=" + caseId);
        var response = SubmissionJson.parse((review ? checker : maker).get(path, 200));
        var body = SubmissionJson.object().put("commandVersion", 1).put("commandType", kind).put("commandId", UUID.randomUUID().toString())
            .put("warehouseId", original.warehouse).put("transportRecoveryEpoch", "1").put("reason", "ISOLATED reviewed return restoration");
        body.set("expected", response.path("expected"));
        if (suffix.equals("DISCOVER")) body.put("dispatchId", original.dispatch); else body.put("caseId", caseId);
        if (suffix.equals("SUBMIT")) body.set("evidence", evidence());
        return body;
    }
    ObjectNode evidence() {
        var evidence = SubmissionJson.object().put("observedAt", Instant.now().minusSeconds(10).toString())
            .put("place", "ISOLATED returned warehouse bay").put("observation", "RETURNED_IN_WAREHOUSE")
            .put("reference", "ISOLATED inspected same barcode and original physical return");
        evidence.putArray("attachments").add(attachment.deepCopy()); return evidence;
    }
    byte[] signed(CorrectionAttachmentHttp actor, ObjectNode body) throws Exception {
        byte[] bytes = actor.command(body, 200); SupervisorCorrectionReceipt.verify(bytes, original.publicKey);
        Files.writeString(original.evidence.resolve(body.path("commandId").asText() + "-command.json"), SubmissionJson.canonical(body));
        Files.write(original.evidence.resolve(body.path("commandId").asText() + "-receipt.json"), bytes);
        assertThat(actor.command(body, 200)).containsExactly(bytes); return bytes;
    }
    void discover() throws Exception {
        caseId = SubmissionJson.parse(signed(maker, command("DISCOVER", false))).at("/businessDecision/caseId").asText();
    }
    void submit() throws Exception { signed(maker, command("SUBMIT", false)); }
    JsonNode state() {
        var value = SubmissionJson.object(); value.set("result", original.result()); value.set("original", original.original());
        value.put("ledgerCount", original.postings()).put("ledgerWeight", original.correctionWeight());
        value.set("stock", SubmissionJson.parse(original.jdbc.queryForObject("SELECT JSON_OBJECT('bookStatus',p.book_status,"
            + "'stockVersion',p.row_version,'cycle',b.dispatch_cycle,'blockedReason',p.blocked_reason,'upstreamPresent',p.upstream_present)"
            + " FROM bale b JOIN inventory_position p ON p.bale_id=b.id WHERE b.id=UUID_TO_BIN(?)", String.class, bale)));
        return value;
    }
}
