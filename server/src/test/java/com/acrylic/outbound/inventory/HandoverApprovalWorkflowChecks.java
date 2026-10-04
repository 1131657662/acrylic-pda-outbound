package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Uses actual recovery/stop/abort handlers and a separate real supervisor session. Physical descriptions are fixtures. */
public final class HandoverApprovalWorkflowChecks {
    final HandoverStopFixture f;
    final HandoverApprovalHttp web;
    final HandoverStopFacts facts;
    private final String username;
    private final Map<String, ObjectNode> commands = new LinkedHashMap<>();
    private final Map<String, byte[]> receipts = new LinkedHashMap<>();

    public HandoverApprovalWorkflowChecks(WebApplicationContext app, Path attempt) throws Exception {
        this(app, attempt, false);
    }

    public HandoverApprovalWorkflowChecks(WebApplicationContext app, Path attempt, boolean androidRecords) throws Exception {
        var directory = attempt.resolve("approval-workflow");
        f = androidRecords ? HandoverStopFixture.forAndroid(app, directory) : new HandoverStopFixture(app, directory);
        facts = new HandoverStopFacts(f, f.jdbc);
        UUID actor = Ids.next(); username = "handover-supervisor-" + actor;
        f.jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(actor), username, "审批测试", app.getBean(PasswordEncoder.class).encode("Test-password-123"));
        f.jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(actor), Ids.bytes(f.r.f.web.identity().warehouse()));
        f.jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='SUPERVISOR'", Ids.bytes(actor));
        web = new HandoverApprovalHttp(username, f.directory);
    }

    public void verify() throws Exception {
        f.post(f.command, f.r.f.tokens, 200, null); abort();
        var approve = command("APPROVE"); checklistFailures(approve); missingFile(approve);
        writeFailure(approve); new HandoverApprovalDriftChecks(this).verify(approve);
        remember(approve, web.command(approve, 200, null));
        var before = facts.snapshot(); assertThat(web.command(approve, 200, null)).isEqualTo(receipts.get("APPROVE")); facts.unchanged(before);
        var prepare = command("PREPARE"); missingFile(prepare); remember(prepare, web.command(prepare, 200, null));
        var activate = command("ACTIVATE"); missingFile(activate);
        new HandoverApprovalDriftChecks(this).verify(activate);
        remember(activate, web.command(activate, 200, null));
        assertThat(SubmissionJson.parse(receipts.get("APPROVE")).path("businessDecision").path("retained").asText()).isEqualTo("2");
        assertThat(f.jdbc.queryForObject("SELECT status FROM handover_case WHERE id=?", String.class, Ids.bytes(f.kase))).isEqualTo("ACTIVATED");
        replay();
    }

    ObjectNode command(String kind) throws Exception {
        var context = SubmissionJson.parse(web.get("/handovers/" + f.kase + "/context?warehouseId="
            + f.r.f.web.identity().warehouse() + "&commandType=" + kind));
        var body = SubmissionJson.object().put("commandVersion", 1).put("commandType", kind)
            .put("commandId", UUID.randomUUID().toString()).put("warehouseId", f.r.f.web.identity().warehouse().toString())
            .put("caseId", f.kase.toString()).put("transportRecoveryEpoch", "1").put("reason", "真实停止证据批准链");
        body.set("expected", context.path("expected"));
        if (!kind.equals("APPROVE")) return body.put("targetDeviceId", f.r.f.tokens.path("deviceId").asText())
            .put("targetUserId", f.r.f.tokens.path("userId").asText());
        body.put("stopState", "DEVICE_ACKED").put("integrityState", "COMPLETE").put("originalResultState", "ABORTED")
            .put("physicalState", "MATCHED").put("inputHighWatermark", f.command.path("captureHighWatermark").asText());
        var lines = body.putArray("checklist");
        for (var scan : f.r.body.withArray("scans")) lines.addObject().put("baleId", scan.path("baleId").asText())
            .put("deliveryCode", scan.path("deliveryCode").asText()).put("state", "RETAINED")
            .put("dispatchCycle", scan.path("expectedDispatchCycle").asText());
        return body;
    }

    private void checklistFailures(ObjectNode original) throws Exception {
        var missing = original.deepCopy(); missing.withArray("checklist").remove(0); refuse(missing, "HANDOVER_CHECKLIST_MISMATCH");
        var duplicate = original.deepCopy(); duplicate.withArray("checklist").set(1, duplicate.path("checklist").get(0)); refuse(duplicate, "HANDOVER_CHECKLIST_MISMATCH");
        for (String field : List.of("baleId", "deliveryCode", "dispatchCycle")) {
            var changed = original.deepCopy(); ((ObjectNode) changed.path("checklist").get(0))
                .put(field, field.equals("baleId") ? UUID.randomUUID().toString() : field.equals("dispatchCycle") ? "9" : "wrong");
            refuse(changed, "HANDOVER_CHECKLIST_MISMATCH");
        }
        var high = original.deepCopy().put("inputHighWatermark", "1"); refuse(high, "HANDOVER_EVIDENCE_CHANGED");
        var removed = original.deepCopy(); ((ObjectNode) removed.path("checklist").get(0)).put("state", "REMOVED");
        var before = facts.snapshot(); web.command(removed, 400, "HANDOVER_CHECKLIST_REQUIRED"); facts.unchanged(before);
        ((ObjectNode) removed.path("checklist").get(0)).put("approvedEvidenceId", UUID.randomUUID().toString());
        refuse(removed, "DISPOSITION_EVIDENCE_MISMATCH");
    }

    void refuse(ObjectNode body, String code) throws Exception {
        var before = facts.snapshot(); web.command(body, 409, code); facts.unchanged(before);
    }

    private void missingFile(ObjectNode command) throws Exception {
        var path = f.directory.getParent().resolve("storage/submissions/" + f.command.path("payloadSha256").asText() + ".json");
        var retained = path.resolveSibling(path.getFileName() + "." + UUID.randomUUID() + ".retained"); Files.move(path, retained);
        try { refuse(command, "HANDOVER_STOP_FILE_UNAVAILABLE"); } finally { Files.move(retained, path); }
    }

    private void writeFailure(ObjectNode command) throws Exception {
        f.jdbc.execute("CREATE TRIGGER test_approval_write_failure BEFORE INSERT ON handover_command_receipt FOR EACH ROW BEGIN IF NEW.command_type='APPROVE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='approval receipt failure'; END IF; END");
        try { var before = facts.snapshot(); web.command(command, 500, null); facts.unchanged(before); }
        finally { f.jdbc.execute("DROP TRIGGER test_approval_write_failure"); }
    }

    private void abort() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/submissions/" + f.r.f.id() + "/abort"))
            .timeout(java.time.Duration.ofSeconds(30)).header("Content-Type", "application/json")
            .header("X-Server-Recovery-Epoch", "1").header("Authorization", "Bearer " + f.r.f.tokens.path("accessToken").asText())
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(f.r.f.abortBody()))).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        Files.write(f.directory.resolve("abort.json"), response.body(), StandardOpenOption.CREATE_NEW);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(SubmissionJson.parse(response.body()).path("data").path("status").asText()).isEqualTo("ABORTED");
    }

    private void remember(ObjectNode command, byte[] receipt) throws Exception {
        var kind = command.path("commandType").asText(); commands.put(kind, command); receipts.put(kind, receipt);
        Files.write(f.directory.resolve(kind + "-receipt.json"), receipt, StandardOpenOption.CREATE_NEW);
        Files.writeString(f.directory.resolve(kind + "-command.json"), command.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }
    private void replay() throws Exception {
        var before = facts.snapshot();
        for (String kind : commands.keySet()) assertThat(web.command(commands.get(kind), 200, null)).isEqualTo(receipts.get(kind));
        facts.unchanged(before);
    }
    public void restart(WebApplicationContext app) throws Exception {
        var client = new HandoverApprovalHttp(username, f.directory);
        var current = new HandoverStopFacts(f, app.getBean(org.springframework.jdbc.core.JdbcTemplate.class)); var before = current.snapshot();
        for (String kind : commands.keySet()) assertThat(client.command(commands.get(kind), 200, null)).isEqualTo(receipts.get(kind));
        current.unchanged(before);
    }
}
