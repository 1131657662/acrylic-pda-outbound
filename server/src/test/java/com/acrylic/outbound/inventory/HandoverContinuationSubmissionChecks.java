package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Final HTTP submission preserves origin facts, enforces per-device ordering, and commits accounting once. */
final class HandoverContinuationSubmissionChecks {
    void verify(HandoverContinuationFixture f, JsonNode continuation) throws Exception {
        var body = f.continuationSubmission(continuation);
        addDiscardedInputs(body, continuation.path("assignment"));
        rejectEvidenceChanges(f, body);
        rejectNewIdentityChanges(f, body);
        rejectSequenceChanges(f, body);
        rollbackAccountingFailure(f, body);
        var receipt = SubmissionJson.parse(f.submit(body, f.tokens, 200)).path("data");
        assertThat(receipt.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(receipt.path("workRound").asText()).isEqualTo("2");
        var beforeReplay = f.workflow.facts.snapshot();
        assertThat(SubmissionJson.parse(f.submit(body, f.tokens, 200)).path("data")).isEqualTo(receipt);
        f.workflow.facts.unchanged(beforeReplay);
        assertAccounting(f, continuation, body);
        Files.writeString(f.source.directory.resolve("final-submission-receipt.json"), receipt.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    private void addDiscardedInputs(ObjectNode body, JsonNode target) {
        var events = body.withArray("events"); var freeze = (ObjectNode) events.remove(events.size() - 1);
        for (int i = 1; i <= 2; i++) {
            var capture = ((ObjectNode) body.path("captures").get(0)).deepCopy();
            capture.put("id", UUID.randomUUID().toString()).put("assignmentId", target.path("id").asText())
                .put("originDeviceId", target.path("deviceId").asText()).put("operatorId", target.path("operatorId").asText())
                .put("authorizationId", target.path("offlineAuthorizationId").asText()).put("assignmentEpoch", target.path("epoch").asText())
                .put("orderScanSeq", Integer.toString(i + 2)).put("deviceCaptureSeq", Integer.toString(i))
                .put("inputSource", "MANUAL").put("confirmationPolicy", "MANUAL").put("state", "DISCARDED")
                .put("rawInput", "").putNull("barcode").putNull("expectedDispatchCycle").putNull("confirmedAt");
            body.withArray("captures").add(capture);
            events.add(freeze.deepCopy().put("id", UUID.randomUUID().toString()).put("captureId", capture.path("id").asText())
                .put("localSeq", Integer.toString(i * 2 - 1)).put("type", "RECEIVED").put("detail", "MANUAL"));
            events.add(freeze.deepCopy().put("id", UUID.randomUUID().toString()).put("captureId", capture.path("id").asText())
                .put("localSeq", Integer.toString(i * 2)).put("type", "DISCARDED").put("detail", ""));
        }
        events.add(freeze.put("localSeq", "5"));
        body.put("captureHighWatermark", "4");
        ((ObjectNode) body.path("captureSummary")).put("received", "4").put("discarded", "2");
    }

    private void rejectEvidenceChanges(HandoverContinuationFixture f, ObjectNode body) throws Exception {
        var changed = body.deepCopy(); ((ObjectNode) changed.path("captures").get(0)).put("operatorId", UUID.randomUUID().toString());
        reject(f, changed, 409, "RETAINED_SET_MISMATCH");
        changed = body.deepCopy(); changed.withArray("events").remove(0);
        reject(f, changed, 409, "RETAINED_SET_MISMATCH");
        changed = body.deepCopy(); changed.withArray("dispositions").addObject().put("captureId", body.path("captures").get(0).path("id").asText());
        reject(f, changed, 409, "DISPOSITION_EVIDENCE_MISMATCH");
        var before = f.workflow.facts.snapshot();
        assertThat(SubmissionJson.parse(f.submit(body, f.source.r.f.tokens, 404)).path("code").asText()).isEqualTo("SUBMISSION_NOT_FOUND");
        f.workflow.facts.unchanged(before);
    }

    private void rejectNewIdentityChanges(HandoverContinuationFixture f, ObjectNode body) throws Exception {
        for (String field : new String[]{"originDeviceId", "operatorId"}) {
            var changed = body.deepCopy(); var events = changed.withArray("events");
            ((ObjectNode) events.get(events.size() - 1)).put(field, UUID.randomUUID().toString());
            reject(f, changed, 400, "INVALID_SUBMISSION");
            changed = body.deepCopy(); ((ObjectNode) changed.path("captures").get(2)).put(field, UUID.randomUUID().toString());
            reject(f, changed, 400, "INVALID_SUBMISSION");
        }
        var changed = body.deepCopy();
        ((ObjectNode) changed.path("captures").get(2)).put("assignmentId", f.source.r.f.assignment.path("id").asText());
        reject(f, changed, 400, "INVALID_SUBMISSION");
    }

    private void rejectSequenceChanges(HandoverContinuationFixture f, ObjectNode body) throws Exception {
        var changed = body.deepCopy(); ((ObjectNode) changed.path("captures").get(3)).put("deviceCaptureSeq", "1");
        reject(f, changed, 400, "INVALID_SUBMISSION");
        changed = body.deepCopy(); ((ObjectNode) changed.path("captures").get(2)).put("orderScanSeq", "4");
        reject(f, changed, 400, "INVALID_SUBMISSION");
        changed = body.deepCopy(); var events = changed.withArray("events");
        ((ObjectNode) events.get(events.size() - 1)).put("localSeq", "4");
        reject(f, changed, 400, "INVALID_SUBMISSION");
    }

    private void reject(HandoverContinuationFixture f, ObjectNode body, int status, String code) throws Exception {
        var before = f.workflow.facts.snapshot();
        assertThat(SubmissionJson.parse(f.submit(body, f.tokens, status)).path("code").asText()).isEqualTo(code);
        f.workflow.facts.unchanged(before);
    }

    private void rollbackAccountingFailure(HandoverContinuationFixture f, ObjectNode body) throws Exception {
        var jdbc = f.source.r.f.jdbc; var before = f.workflow.facts.snapshot();
        int captures = jdbc.queryForObject("SELECT COUNT(*) FROM submitted_capture", Integer.class);
        int events = jdbc.queryForObject("SELECT COUNT(*) FROM submitted_event", Integer.class);
        jdbc.execute("CREATE TRIGGER e2a_continuation_ledger_failure BEFORE INSERT ON inventory_ledger FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='continuation ledger rollback probe'");
        try { f.submit(body, f.tokens, 500); }
        finally { jdbc.execute("DROP TRIGGER e2a_continuation_ledger_failure"); }
        f.workflow.facts.unchanged(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submitted_capture", Integer.class)).isEqualTo(captures);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submitted_event", Integer.class)).isEqualTo(events);
        assertThat(jdbc.queryForObject("SELECT status FROM order_continuation WHERE id=UUID_TO_BIN(?)", String.class,
            body.path("continuationId").asText())).isEqualTo("ACTIVATED");
    }

    private void assertAccounting(HandoverContinuationFixture f, JsonNode continuation, ObjectNode body) throws Exception {
        var jdbc = f.source.r.f.jdbc; var order = f.source.r.f.order;
        assertThat(jdbc.queryForObject("SELECT status FROM order_continuation WHERE id=UUID_TO_BIN(?)", String.class, continuation.path("continuationId").asText())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT phase FROM order_submission_slot WHERE order_id=UUID_TO_BIN(?)", String.class, order)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)", String.class, body.path("submissionId").asText())).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_result_version WHERE order_id=UUID_TO_BIN(?)", Integer.class, order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE order_id=UUID_TO_BIN(?)", Integer.class, order)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger l JOIN dispatch_record d ON d.id=l.dispatch_id WHERE d.order_id=UUID_TO_BIN(?)", Integer.class, order)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT SUM(l.quantity_delta) FROM inventory_ledger l JOIN dispatch_record d ON d.id=l.dispatch_id WHERE d.order_id=UUID_TO_BIN(?)", Long.class, order)).isEqualTo(-2);
        assertThat(jdbc.queryForObject("SELECT SUM(l.weight_delta_g) FROM inventory_ledger l JOIN dispatch_record d ON d.id=l.dispatch_id WHERE d.order_id=UUID_TO_BIN(?)", Long.class, order)).isEqualTo(-601500);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE order_id=UUID_TO_BIN(?) AND state='DISPATCHED'", Integer.class, order)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(superseded_by) FROM submission WHERE id=UUID_TO_BIN(?)", String.class, f.source.r.f.id())).isEqualTo(continuation.path("continuationId").asText());
        var original = Files.readAllBytes(f.source.directory.getParent().resolve("storage/submissions/" + SubmissionJson.sha(f.source.r.body) + ".json"));
        assertThat(SubmissionJson.parse(original)).isEqualTo(f.source.r.body);
    }
}
