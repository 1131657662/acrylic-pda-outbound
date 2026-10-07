package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.handover.HandoverApprovalEvidenceProbe;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual TEMP stop and separate supervisor HTTP approval; the original source and stock remain unchanged. */
public final class HandoverTemporaryApprovalChecks {
    private final HandoverTemporaryStopFixture f;
    private final HandoverApprovalHttp reviewer;
    private final String username;
    private final UUID supervisor;
    private final JsonNode originalBody;
    private final TemporaryDispositionSqlChecks facts;
    private ObjectNode approved;
    private byte[] receipt;

    public HandoverTemporaryApprovalChecks(WebApplicationContext app, Path attempt) throws Exception {
        f = new HandoverTemporaryStopFixture(app, attempt); f.abort(); stopped();
        var identity = IdentityFixture.create(f.jdbc, app.getBean(PasswordEncoder.class), "SUPERVISOR");
        supervisor = identity.user().id(); username = identity.user().username();
        f.jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(supervisor), Ids.bytes(f.head.warehouse()));
        reviewer = new HandoverApprovalHttp(username, f.directory);
        String hash = f.jdbc.queryForObject("SELECT payload_sha256 FROM submission_payload WHERE submission_id=?", String.class,
            Ids.bytes(f.head.control().id()));
        originalBody = SubmissionJson.parse(app.getBean(SubmissionFiles.class).read(hash));
        facts = new TemporaryDispositionSqlChecks(f.jdbc, f.directory.resolve("approval-sql"));
    }

    public void verify(WebApplicationContext app) throws Exception {
        var before = facts.snapshot("read-before");
        var consumed = HandoverApprovalEvidenceProbe.consume(app, f.kase, f.command.path("orderScanHighWatermark").asLong());
        assertThat(consumed).isEqualTo(originalBody);
        assertThat(facts.snapshot("read-after")).isEqualTo(before);
        approved = command(reviewer, "APPROVE");
        refusal(approved.deepCopy().put("inputHighWatermark", "999"), "HANDOVER_EVIDENCE_CHANGED", "high");
        var incomplete = approved.deepCopy(); incomplete.withArray("checklist").remove(0);
        refusal(incomplete, "HANDOVER_CHECKLIST_MISMATCH", "checklist");
        selfApproval(); missingFile(); corruptFile(); untrustedKey(); stockConflict(); revoked(); rollback();
        before = facts.snapshot("approval-before"); concurrent();
        var after = facts.snapshot("approval-after");
        for (String table : before.keySet()) if (!Set.of("api_idempotency_request", "operation_event").contains(table))
            assertThat(after.get(table)).as(table).isEqualTo(before.get(table));
        assertThat(SubmissionJson.parse(receipt).path("businessDecision").path("status").asText()).isEqualTo("APPROVED");
        assertThat(f.jdbc.queryForObject("SELECT BIN_TO_UUID(approved_by) FROM handover_case WHERE id=?", String.class,
            Ids.bytes(f.kase))).isEqualTo(supervisor.toString());
        assertThat(reviewer.command(approved, 200, null)).isEqualTo(receipt);
        reviewer.command(approved.deepCopy().put("reason", "changed"), 409, "IDEMPOTENCY_CONTENT_MISMATCH");
        reviewer.command(command(reviewer, "PREPARE"), 400, "HANDOVER_TARGET_AUTHORIZATION_REQUIRED");
        f.sync();
    }

    private void stopped() throws Exception {
        assertThat(f.command.path("captureHighWatermark").asLong()).isGreaterThan(f.command.path("orderScanHighWatermark").asLong());
        f.stop(f.command.deepCopy().put("orderScanHighWatermark", "999"), 409, "HANDOVER_STOP_CONTEXT_CHANGED");
        var legacy = f.command.deepCopy(); legacy.remove("orderScanHighWatermark");
        f.stop(legacy, 409, "HANDOVER_STOP_CONTEXT_CHANGED");
        byte[] stopped = f.stop(f.command, 200, null);
        Files.write(f.directory.resolve("original-receipt.json"), stopped);
        assertThat(SubmissionJson.parse(stopped).path("temporaryStop").path("orderScanHighWatermark"))
            .isEqualTo(f.command.path("orderScanHighWatermark"));
        assertThat(f.jdbc.queryForObject("SELECT input_high_watermark FROM handover_case WHERE id=?", Long.class,
            Ids.bytes(f.kase))).isEqualTo(f.command.path("orderScanHighWatermark").asLong());
    }

    private ObjectNode command(HandoverApprovalHttp client, String kind) throws Exception {
        var context = SubmissionJson.parse(client.get("/handovers/" + f.kase + "/context?warehouseId=" + f.head.warehouse()
            + "&commandType=" + kind));
        var body = SubmissionJson.object().put("commandVersion", 1).put("commandType", kind)
            .put("commandId", UUID.randomUUID().toString()).put("warehouseId", f.head.warehouse().toString())
            .put("caseId", f.kase.toString()).put("transportRecoveryEpoch", "1").put("reason", "临时来源主管实物核对");
        body.set("expected", context.path("expected"));
        if (!kind.equals("APPROVE")) return body.put("targetDeviceId", f.head.control().device().toString())
            .put("targetUserId", f.head.actor().toString());
        body.put("stopState", "DEVICE_ACKED").put("integrityState", "COMPLETE").put("originalResultState", "ABORTED")
            .put("physicalState", "MATCHED").put("inputHighWatermark", f.command.path("orderScanHighWatermark").asText());
        var lines = body.putArray("checklist");
        for (var scan : originalBody.path("scans")) if (scan.path("status").asText().equals("ACTIVE"))
            lines.addObject().put("baleId", scan.path("baleId").asText()).put("deliveryCode", scan.path("deliveryCode").asText())
                .put("state", "RETAINED").put("dispatchCycle", scan.path("expectedDispatchCycle").asText());
        return body;
    }

    private void selfApproval() throws Exception {
        var before = facts.snapshot("self-before");
        f.web.command(command(f.web, "APPROVE"), 403, "HANDOVER_SELF_APPROVAL_DENIED");
        assertThat(facts.snapshot("self-after")).isEqualTo(before);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM handover_checklist WHERE case_id=?", Long.class, Ids.bytes(f.kase))).isZero();
    }

    private void missingFile() throws Exception {
        String hash = f.jdbc.queryForObject("SELECT payload_sha256 FROM submission_payload WHERE submission_id=?", String.class,
            Ids.bytes(f.head.control().id()));
        var body = f.directory.getParent().resolve("storage/submissions/" + hash + ".json");
        unavailable(body, "HANDOVER_STOP_FILE_UNAVAILABLE", "body");
        String key = f.jdbc.queryForObject("SELECT file_key FROM source_evidence_object WHERE submission_id=? AND kind='MANIFEST_PAGE' LIMIT 1",
            String.class, Ids.bytes(f.head.control().id()));
        unavailable(f.directory.getParent().resolve("storage").resolve(key), "E2A_PAGE_FILE_UNAVAILABLE", "page");
    }

    private void unavailable(Path path, String code, String label) throws Exception {
        var saved = path.resolveSibling(path.getFileName() + ".d06-approval-retained"); Files.move(path, saved);
        try { refusal(approved, code, "missing-" + label); }
        finally { Files.move(saved, path); }
    }

    private void stockConflict() throws Exception {
        String bale = originalBody.path("scans").get(0).path("baleId").asText();
        f.jdbc.update("UPDATE inventory_position SET blocked_reason='D06_HOLD' WHERE bale_id=UUID_TO_BIN(?)", bale);
        try { refusal(approved, "HANDOVER_STOCK_CONFLICT", "stock"); }
        finally { f.jdbc.update("UPDATE inventory_position SET blocked_reason=NULL WHERE bale_id=UUID_TO_BIN(?)", bale); }
    }

    private void corruptFile() throws Exception {
        String hash = f.jdbc.queryForObject("SELECT payload_sha256 FROM submission_payload WHERE submission_id=?", String.class,
            Ids.bytes(f.head.control().id()));
        var body = f.directory.getParent().resolve("storage/submissions/" + hash + ".json");
        var saved = body.resolveSibling(body.getFileName() + ".d06-original"); Files.move(body, saved);
        try { Files.writeString(body, "{}"); refusal(approved, "HANDOVER_STOP_FILE_UNAVAILABLE", "corrupt-body"); }
        finally {
            Files.move(body, body.resolveSibling(body.getFileName() + ".d06-corrupt"));
            Files.copy(saved, body);
        }
    }

    private void untrustedKey() throws Exception {
        String hash = f.jdbc.queryForObject("SELECT key_sha256 FROM device WHERE id=?", String.class, Ids.bytes(f.head.control().device()));
        f.jdbc.update("UPDATE device SET key_sha256=? WHERE id=?", "0".repeat(64), Ids.bytes(f.head.control().device()));
        try { refusal(approved, "E2A_STOP_KEY_UNVERIFIABLE", "key"); }
        finally { f.jdbc.update("UPDATE device SET key_sha256=? WHERE id=?", hash, Ids.bytes(f.head.control().device())); }
    }

    private void revoked() throws Exception {
        f.jdbc.update("DELETE FROM user_role WHERE user_id=?", Ids.bytes(supervisor));
        try { reviewer.command(approved, 403, "PERMISSION_DENIED"); }
        finally { f.jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='SUPERVISOR'", Ids.bytes(supervisor)); }
    }

    private void rollback() throws Exception {
        var before = facts.snapshot("rollback-before");
        f.jdbc.execute("CREATE TRIGGER d06_temp_approval_failure AFTER INSERT ON operation_event FOR EACH ROW "
            + "BEGIN IF NEW.event_type='HANDOVER_APPROVE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='D06_TEMP_APPROVAL_LAST_WRITE'; END IF; END");
        try { reviewer.command(approved, 500, "INTERNAL_ERROR"); }
        finally { f.jdbc.execute("DROP TRIGGER d06_temp_approval_failure"); }
        assertThat(facts.snapshot("rollback-after")).isEqualTo(before);
        assertThat(f.jdbc.queryForObject("SELECT status FROM handover_case WHERE id=?", String.class, Ids.bytes(f.kase))).isEqualTo("EVIDENCE_PENDING");
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM handover_checklist WHERE case_id=?", Long.class, Ids.bytes(f.kase))).isZero();
    }

    private void concurrent() throws Exception {
        try (var connection = f.jdbc.getDataSource().getConnection(); var workers = Executors.newFixedThreadPool(2)) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) { statement.executeQuery("SELECT id FROM user_management_guard WHERE id=1 FOR UPDATE").close(); }
            var first = workers.submit(() -> reviewer.command(approved, 200, null));
            var second = workers.submit(() -> reviewer.command(approved, 200, null));
            int waits = 0; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (waits < 2 && System.nanoTime() < deadline) {
                waits = f.jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits", Integer.class);
                if (waits < 2) Thread.sleep(50);
            }
            assertThat(waits).isGreaterThanOrEqualTo(2); connection.commit();
            receipt = first.get(30, TimeUnit.SECONDS); assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo(receipt);
        }
        Files.write(f.directory.resolve("approved-original.json"), receipt);
        Files.writeString(f.directory.resolve("approved-command.json"), SubmissionJson.canonical(approved));
    }

    private void refusal(ObjectNode command, String code, String label) throws Exception {
        var before = facts.snapshot(label + "-before"); reviewer.command(command, 409, code);
        assertThat(facts.snapshot(label + "-after")).isEqualTo(before);
    }

    public void restart(WebApplicationContext app) throws Exception {
        assertThat(new HandoverApprovalHttp(username, f.directory).command(approved, 200, null)).isEqualTo(receipt);
        assertThat(HandoverApprovalEvidenceProbe.consume(app, f.kase, f.command.path("orderScanHighWatermark").asLong())).isEqualTo(originalBody);
    }

    TargetContext targetContext() { return new TargetContext(f, reviewer, originalBody, username); }
    record TargetContext(HandoverTemporaryStopFixture source, HandoverApprovalHttp reviewer, JsonNode body, String username) { }
}
