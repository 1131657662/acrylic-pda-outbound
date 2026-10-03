package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual physical-disposition approval endpoint supplies removal references; physical observations remain test input. */
public final class HandoverRemovedChecks {
    private final HandoverStopFixture f;
    private final HandoverApprovalHttp web;
    private final HandoverStopFacts facts;
    public HandoverRemovedChecks(WebApplicationContext app, Path attempt) throws Exception {
        f = new HandoverStopFixture(app, attempt.resolve("removed-workflow"));
        web = new HandoverApprovalHttp(f.r.f.web.identity().user().username(), f.directory); facts = new HandoverStopFacts(f, f.jdbc);
    }
    public void verify() throws Exception {
        f.post(f.command, f.r.f.tokens, 200, null);
        assertThat(f.r.f.abort().path("status").asText()).isEqualTo("ABORTED");
        var command = command("APPROVE").put("stopState", "DEVICE_ACKED").put("integrityState", "COMPLETE")
            .put("originalResultState", "ABORTED").put("physicalState", "MATCHED").put("inputHighWatermark", "2");
        var evidence = approveRemoval(); var lines = command.putArray("checklist");
        for (var scan : f.r.body.path("scans")) {
            var id = evidence.stream().filter(row -> row.path("captureId").equals(scan.path("captureId"))).findFirst().orElseThrow();
            lines.addObject().put("baleId", scan.path("baleId").asText()).put("deliveryCode", scan.path("deliveryCode").asText())
                .put("state", "REMOVED").put("dispatchCycle", scan.path("expectedDispatchCycle").asText())
                .put("approvedEvidenceId", id.path("approvedEvidenceId").asText());
        }
        refusalCases(command); var held = facts.snapshot().path("bale_commitment");
        var receipt = SubmissionJson.parse(web.command(command, 200, null));
        assertThat(receipt.path("businessDecision").path("retained").asText()).isEqualTo("0");
        assertThat(facts.snapshot().path("bale_commitment")).isEqualTo(held);
        for (String kind : List.of("PREPARE", "ACTIVATE")) {
            var next = command(kind); cycleDrift(next); var result = SubmissionJson.parse(web.command(next, 200, null));
            assertThat(result.path("businessDecision").path("status").asText()).isEqualTo(kind.equals("PREPARE") ? "TARGET_PREPARED" : "ACTIVATED");
            assertThat(facts.snapshot().path("bale_commitment")).isEqualTo(held);
        }
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM physical_disposition_evidence WHERE recovery_package_id=? AND applied_submission_id IS NOT NULL",
            Integer.class, Ids.bytes(UUID.fromString(f.command.path("recoveryPackageId").asText())))).isZero();
        Files.writeString(f.directory.resolve("approved-removed.json"), receipt.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }
    private ObjectNode command(String kind) throws Exception {
        var context = SubmissionJson.parse(web.get("/handovers/" + f.kase + "/context?warehouseId="
            + f.r.f.web.identity().warehouse() + "&commandType=" + kind));
        var command = SubmissionJson.object().put("commandVersion", 1).put("commandType", kind)
            .put("commandId", UUID.randomUUID().toString()).put("warehouseId", f.r.f.web.identity().warehouse().toString())
            .put("caseId", f.kase.toString()).put("transportRecoveryEpoch", "1");
        command.set("expected", context.path("expected"));
        if (!kind.equals("APPROVE")) command.put("targetDeviceId", f.r.f.tokens.path("deviceId").asText()).put("targetUserId", f.r.f.tokens.path("userId").asText());
        return command;
    }
    private void cycleDrift(ObjectNode command) throws Exception {
        var bale = f.r.body.path("scans").get(0).path("baleId").asText();
        f.jdbc.update("UPDATE bale SET dispatch_cycle=dispatch_cycle+1 WHERE id=UUID_TO_BIN(?)", bale);
        try { var before = facts.snapshot(); web.command(command, 409, "DISPATCH_CYCLE_CONFLICT"); facts.unchanged(before); }
        finally { f.jdbc.update("UPDATE bale SET dispatch_cycle=dispatch_cycle-1 WHERE id=UUID_TO_BIN(?)", bale); }
    }
    private List<JsonNode> approveRemoval() throws Exception {
        var body = SubmissionJson.object().put("payloadSha256", f.command.path("payloadSha256").asText())
            .put("expectedVersion", f.jdbc.queryForObject("SELECT CAST(row_version AS CHAR) FROM outbound_order WHERE id=UUID_TO_BIN(?)", String.class, f.r.f.order));
        int i = 0; var rows = body.putArray("dispositions");
        for (var scan : f.r.body.path("scans")) rows.addObject().put("captureId", scan.path("captureId").asText())
            .put("expectedDispatchCycle", scan.path("expectedDispatchCycle").asText()).put("action", i++ == 0 ? "RELEASE" : "QUARANTINE")
            .put("physicalEvidence", "隔离测试实物核对记录，不代表现场事实");
        var response = SubmissionJson.parse(web.post("/recovery-packages/" + f.command.path("recoveryPackageId").asText()
            + "/disposition-evidence?warehouseId=" + f.r.f.web.identity().warehouse(), body, 200, null));
        var result = new ArrayList<JsonNode>(); response.path("data").path("evidence").forEach(result::add); return result;
    }
    private void refusalCases(ObjectNode command) throws Exception {
        var wrong = command.deepCopy(); ((ObjectNode) wrong.path("checklist").get(0)).put("approvedEvidenceId", command.path("checklist").get(1).path("approvedEvidenceId").asText());
        var before = facts.snapshot(); web.command(wrong, 409, "DISPOSITION_EVIDENCE_MISMATCH"); facts.unchanged(before);
        var retain = command.deepCopy(); var line = (ObjectNode) retain.path("checklist").get(0); line.put("state", "RETAINED"); line.remove("approvedEvidenceId");
        web.command(retain, 409, "DISPOSITION_EVIDENCE_REQUIRED"); facts.unchanged(before);
    }
}