package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** HTTP edge cases: empty effective set retains input highwater; SQL adversarial rows never become trusted stop proof. */
public final class HandoverApprovalEdgeChecks {
    private final HandoverBusinessSource source;
    private final HandoverApprovalHttp web;
    private final JdbcTemplate jdbc;
    private final Path directory;
    private UUID kase;

    public HandoverApprovalEdgeChecks(WebApplicationContext app, Path attempt, int retained, String name) throws Exception {
        directory = Files.createDirectory(attempt.resolve(name)); jdbc = app.getBean(JdbcTemplate.class);
        source = new HandoverBusinessSource(app, retained); web = new HandoverApprovalHttp(source.username, directory);
    }
    public void empty() throws Exception {
        request(); source.stopAndAbort(kase);
        var approve = command("APPROVE"); source.approveFields(approve);
        var receipt = SubmissionJson.parse(web.command(approve, 200, null));
        assertThat(receipt.path("businessDecision").path("retained").asText()).isEqualTo("0");
        assertThat(receipt.path("businessDecision").path("inputHighWatermark").asText()).isEqualTo("2");
        var prepare = command("PREPARE");
        jdbc.update("UPDATE warehouse SET mapping_revision=mapping_revision+1 WHERE id=?", Ids.bytes(source.warehouse));
        try { web.command(prepare, 409, "ORDER_BASELINE_CHANGED"); }
        finally { jdbc.update("UPDATE warehouse SET mapping_revision=mapping_revision-1 WHERE id=?", Ids.bytes(source.warehouse)); }
        web.command(prepare, 200, null); var activate = command("ACTIVATE"); var original = web.command(activate, 200, null);
        assertThat(SubmissionJson.parse(original).path("businessDecision").path("status").asText()).isEqualTo("ACTIVATED");
        assertThat(web.command(activate, 200, null)).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE order_id=?", Integer.class, Ids.bytes(source.order))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM handover_checklist WHERE case_id=?", Integer.class, Ids.bytes(kase))).isZero();
        Files.writeString(directory.resolve("verified.txt"), "All original captures retained; both scans voided before recovery; input highwater 2; empty APPROVE/PREPARE/ACTIVATE; no commitments.");
    }
    public void fakeProof() throws Exception {
        request(); source.abortOriginal(); var approve = command("APPROVE"); source.approveFields(approve);
        web.command(approve, 409, "HANDOVER_STOP_NOT_PROVEN");
        byte[] fake = "{\"signedReceipt\":\"not.a.signature\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (String kind : List.of("STOP_PROOF", "RECOVERY_PACKAGE")) jdbc.update(
            "INSERT INTO handover_evidence(id,case_id,kind,device_id,payload_sha256,high_watermark,receipt_bytes,recorded_at) VALUES(?,?,?,?,?,2,?,UTC_TIMESTAMP(3))",
            Ids.bytes(Ids.next()), Ids.bytes(kase), kind, Ids.bytes(source.device), new byte[32], fake);
        web.command(approve, 409, "HANDOVER_RECEIPT_INTEGRITY_FAILURE");
        assertThat(jdbc.queryForObject("SELECT status FROM handover_case WHERE id=?", String.class, Ids.bytes(kase))).isEqualTo("EVIDENCE_PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM handover_command_receipt WHERE case_id=?", Integer.class, Ids.bytes(kase))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM handover_checklist WHERE case_id=?", Integer.class, Ids.bytes(kase))).isZero();
        Files.writeString(directory.resolve("verified.txt"), "Missing pair and unsigned adversarial pair refused at actual approval HTTP entry; no approval receipt/checklist; immutable fake rows retained.");
    }
    private void request() throws Exception {
        var context = SubmissionJson.parse(web.get("/handovers/scopes/" + source.scope + "/context?warehouseId=" + source.warehouse));
        var body = base("REQUEST").put("scopeId", source.scope.toString()); body.set("expected", context.path("expected"));
        kase = UUID.fromString(SubmissionJson.parse(web.command(body, 200, null)).path("businessDecision").path("caseId").asText());
    }
    private ObjectNode command(String kind) throws Exception {
        var context = SubmissionJson.parse(web.get("/handovers/" + kase + "/context?warehouseId=" + source.warehouse + "&commandType=" + kind));
        var body = base(kind).put("caseId", kase.toString()); body.set("expected", context.path("expected"));
        if (!kind.equals("APPROVE")) body.put("targetDeviceId", source.device.toString()).put("targetUserId", source.operator.toString());
        return body;
    }
    private ObjectNode base(String kind) {
        return SubmissionJson.object().put("commandVersion", 1).put("commandType", kind).put("commandId", UUID.randomUUID().toString())
            .put("warehouseId", source.warehouse.toString()).put("transportRecoveryEpoch", "1").put("reason", "隔离边界验证");
    }
}