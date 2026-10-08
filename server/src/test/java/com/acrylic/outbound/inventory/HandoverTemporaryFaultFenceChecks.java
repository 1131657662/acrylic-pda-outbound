package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP supervisor fault fencing for an already archived TEMP source. */
public final class HandoverTemporaryFaultFenceChecks {
    final HandoverTemporaryStopFixture source;
    final HandoverApprovalHttp faultReviewer;
    final Path directory;
    final JsonNode body;
    final UUID supervisor;

    public HandoverTemporaryFaultFenceChecks(WebApplicationContext app, Path attempt) throws Exception {
        source = new HandoverTemporaryStopFixture(app, attempt);
        directory = source.directory.resolve("fault-fence"); Files.createDirectory(directory);
        source.abort();
        String hash = source.jdbc.queryForObject("SELECT payload_sha256 FROM submission_payload WHERE submission_id=?", String.class,
            Ids.bytes(source.head.control().id()));
        body = SubmissionJson.parse(app.getBean(com.acrylic.outbound.submissions.SubmissionFiles.class).read(hash));
        var identity = IdentityFixture.create(source.jdbc, app.getBean(PasswordEncoder.class), "SUPERVISOR");
        supervisor = identity.user().id();
        source.jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(supervisor), Ids.bytes(source.head.warehouse()));
        faultReviewer = new HandoverApprovalHttp(identity.user().username(), directory);
    }

    public void verify() throws Exception {
        verify(false);
    }

    public void verifyAttachments() throws Exception {
        verify(true);
    }

    private void verify(boolean withAttachments) throws Exception {
        new HandoverFaultRejectionChecks(this).verify();
        var valid = faultCommand();
        var attachments = withAttachments ? new HandoverFaultAttachmentChecks(this) : null;
        if (attachments != null) attachments.prepare(valid);
        byte[] receipt = "true".equals(System.getenv("HANDOVER_FAULT_BROWSER_VERIFY"))
            ? HandoverFaultBrowserChecks.execute(this, valid) : HandoverFaultConcurrentChecks.execute(this, valid);
        Files.write(directory.resolve("fault-receipt.json"), receipt);
        var parsed = SubmissionJson.parse(receipt); var decision = parsed.path("businessDecision");
        assertThat(decision.path("status").asText()).isEqualTo("FAULT_FENCED");
        assertThat(decision.path("stopKind").asText()).isEqualTo("FAULT_FENCE");
        assertThat(decision.path("oldDeviceAcknowledged").asBoolean()).isFalse();
        assertThat(parsed.path("caseContext").path("stopState").asText()).isEqualTo("REQUESTED");
        assertThat(faultReviewer.command(valid, 200, null)).isEqualTo(receipt);
        faultReviewer.command(valid.deepCopy().put("reason", "异文"), 409, "IDEMPOTENCY_CONTENT_MISMATCH");
        fenced(); disabled();
        HandoverFaultReadChecks.verify(this);
        if (attachments != null) attachments.recorded(valid, receipt);
        approveAfterFence(faultReviewer);
    }

    private void fenced() {
        assertThat(source.jdbc.queryForObject("SELECT stop_state FROM handover_case WHERE id=?", String.class, Ids.bytes(source.kase)))
            .isEqualTo("FAULT_FENCED");
        assertThat(source.jdbc.queryForObject("SELECT status FROM device WHERE id=?", String.class, Ids.bytes(source.head.control().device())))
            .isEqualTo("DISABLED");
        assertThat(source.jdbc.queryForObject("SELECT COUNT(*) FROM pda_session WHERE device_id=? AND revoked_at IS NOT NULL", Long.class,
            Ids.bytes(source.head.control().device()))).isGreaterThan(0);
        assertThat(source.jdbc.queryForObject("SELECT COUNT(*) FROM order_sync_outbox WHERE event_type='HANDOVER_FAULT_FENCED' AND order_id=?", Long.class,
            Ids.bytes(source.head.order()))).isEqualTo(1);
        assertThat(source.jdbc.queryForObject("SELECT COUNT(*) FROM handover_evidence WHERE case_id=?", Long.class,
            Ids.bytes(source.kase))).isZero();
    }

    private void disabled() throws Exception {
        var device = Ids.bytes(source.head.control().device());
        String version = source.jdbc.queryForObject("SELECT CAST(row_version AS CHAR) FROM device WHERE id=?", String.class, device);
        var facts = new HandoverFaultFacts(source.jdbc, directory.resolve("fence-protection"));
        var before = facts.snapshot("before");
        source.web.post("/devices/" + source.head.control().device() + "/status?warehouseId=" + source.head.warehouse(),
            SubmissionJson.object().put("expectedVersion", version).put("status", "ACTIVE").put("reason", "普通启用不可解除故障围栏"),
            409, "DEVICE_FAULT_FENCED");
        assertThatThrownBy(() -> source.jdbc.update("UPDATE device SET status='ACTIVE' WHERE id=?", device))
            .hasMessageContaining("ordinary re-enable is forbidden");
        assertThatThrownBy(() -> source.jdbc.update("UPDATE device SET key_sha256=? WHERE id=?", "0".repeat(64), device))
            .hasMessageContaining("ordinary re-enable is forbidden");
        assertThatThrownBy(() -> source.jdbc.update("UPDATE handover_fault_fence SET input_high_watermark=0 WHERE case_id=?", Ids.bytes(source.kase)))
            .hasMessageContaining("Fault fence is immutable");
        assertThat(facts.snapshot("after")).isEqualTo(before);
        source.post("/handovers/" + source.kase + "/stop-ack", source.command, source.token, 401);
    }

    public void migration(Path attempt, Runnable migrate) throws Exception { HandoverFaultMigrationChecks.verify(this, attempt, migrate); }

    public HandoverTemporaryAndroidBridge receiving(WebApplicationContext app, Path attempt) throws Exception {
        return new HandoverTemporaryAndroidBridge(app,
            new HandoverTemporaryApprovalChecks.TargetContext(source, faultReviewer, body, faultReviewer.username()), attempt);
    }

    private void approveAfterFence(HandoverApprovalHttp reviewer) throws Exception {
        var context = SubmissionJson.parse(reviewer.get("/handovers/" + source.kase + "/context?warehouseId=" + source.head.warehouse()
            + "&commandType=APPROVE"));
        var approve = objectCommand("APPROVE", context.path("expected"));
        approve.put("stopState", "FAULT_FENCED").put("integrityState", "COMPLETE").put("originalResultState", "ABORTED")
            .put("physicalState", "MATCHED").put("inputHighWatermark", source.command.path("orderScanHighWatermark").asText());
        var lines = approve.putArray("checklist");
        for (var scan : body.path("scans")) if ("ACTIVE".equals(scan.path("status").asText()))
            lines.addObject().put("baleId", scan.path("baleId").asText()).put("deliveryCode", scan.path("deliveryCode").asText())
                .put("state", "RETAINED").put("dispatchCycle", scan.path("expectedDispatchCycle").asText());
        byte[] receipt = reviewer.command(approve, 200, null);
        assertThat(SubmissionJson.parse(receipt).path("businessDecision").path("status").asText()).isEqualTo("APPROVED");
        assertThat(source.jdbc.queryForObject("SELECT stop_state FROM handover_case WHERE id=?", String.class, Ids.bytes(source.kase)))
            .isEqualTo("FAULT_FENCED");
        Files.write(directory.resolve("fault-approve-receipt.json"), receipt);
    }

    ObjectNode faultCommand() throws Exception {
        var context = SubmissionJson.parse(source.web.get("/handovers/" + source.kase + "/context?warehouseId=" + source.head.warehouse()
            + "&commandType=FAULT_FENCE"));
        var row = source.jdbc.queryForMap("""
            SELECT BIN_TO_UUID(v.id) verification,BIN_TO_UUID(e.id) evidence,e.set_sha256,
              CAST(e.capture_high_watermark AS CHAR) capture,CAST(e.audit_through_seq AS CHAR) audit,
              CAST(e.order_scan_high_watermark AS CHAR) input_high
            FROM source_evidence_set e JOIN source_evidence_verification v ON v.set_id=e.id
            WHERE e.submission_id=? AND v.result='VERIFIED_COMPLETE_AT_STOP'
            """, Ids.bytes(source.head.control().id()));
        var result = objectCommand("FAULT_FENCE", context.path("expected"));
        result.remove("reason");
        result.put("sourceDeviceId", source.head.control().device().toString());
        result.putObject("physicalControl").put("deviceState", "POWERED_OFF_IN_CUSTODY")
            .put("controlledAt", java.time.Instant.now().toString()).put("deviceLocation", "隔离保管柜")
            .put("goodsLocation", "交接货区").put("unresolvedInputCount", "0");
        result.putObject("archivedEvidence").put("sourceEvidenceSetId", row.get("evidence").toString())
            .put("verificationId", row.get("verification").toString()).put("setSha256", row.get("set_sha256").toString())
            .put("captureHighWatermark", row.get("capture").toString()).put("auditThroughSeq", row.get("audit").toString())
            .put("orderScanHighWatermark", row.get("input_high").toString());
        return result;
    }

    private ObjectNode objectCommand(String kind, JsonNode expected) {
        return SubmissionJson.object().put("commandVersion", 1).put("commandType", kind).put("commandId", UUID.randomUUID().toString())
            .put("warehouseId", source.head.warehouse().toString()).put("caseId", source.kase.toString())
            .put("transportRecoveryEpoch", "1").put("reason", "故障交接主管核对").set("expected", expected.deepCopy());
    }
}
