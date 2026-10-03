package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.recall.RecallStore;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Read-only consumption of the stop attestation. Command callers must already hold source and case locks. */
@Component
final class HandoverApprovalEvidence {
    private final HandoverStopStore stops;
    private final HandoverStopReceipts receipts;
    private final RecallStore recalls;
    private final SubmissionFiles files;

    HandoverApprovalEvidence(HandoverStopStore stops, HandoverStopReceipts receipts, RecallStore recalls,
            SubmissionFiles files) {
        this.stops = stops; this.receipts = receipts; this.recalls = recalls; this.files = files;
    }

    Verified verify(JsonNode facts, long requestedHigh) {
        var caseId = uuid(facts, "caseId");
        var origin = stops.origin(caseId);
        var stop = stops.original(caseId); var packRow = stops.recovery(caseId);
        if (stop == null || packRow == null) throw new BusinessException(409, "HANDOVER_STOP_NOT_PROVEN", "缺少原机签名停止证明或恢复包证据");
        var receipt = receipts.verifyEvidence(stop, packRow, origin, facts);
        var bound = receipt.path("originalCommand");
        var command = HandoverStopCommand.parse(caseId, text(bound, "transportRecoveryEpoch"),
            HandoverReceiptCodec.bytes(bound.path("command")));
        caseBinding(facts, receipt, command, requestedHigh);
        var recall = recalls.get(command.recallId());
        var pack = recalls.recovery(command.recallId());
        recoveryBinding(origin, command, receipt.path("recallStop"), recall, pack);
        var body = read(command.payloadHash());
        bodyBinding(origin, command, pack, body);
        return new Verified(receipt, body);
    }

    private void caseBinding(JsonNode facts, JsonNode receipt, HandoverStopCommand command, long high) {
        requireSame(high == command.captureHigh() && number(facts, "inputHighWatermark") == high
            && number(facts, "caseVersion") > command.caseVersion()
            && text(facts, "stopState").equals("DEVICE_ACKED") && text(facts, "integrityState").equals("COMPLETE"));
        for (String field : List.of("warehouseId", "scopeId", "submissionId", "canonicalOrderId", "sourceKind", "fromWorkRound", "toWorkRound"))
            requireSame(facts.hasNonNull(field) && facts.path(field).equals(receipt.path("caseContext").path(field)));
    }

    private void recoveryBinding(HandoverStopStore.Origin origin, HandoverStopCommand command, JsonNode stop,
            RecallStore.Head recall, RecallStore.Package pack) {
        requireSame(recall.warehouse().equals(origin.warehouse()) && recall.order().equals(origin.order())
            && recall.assignment().equals(origin.assignment()) && recall.epoch().equals(origin.epoch())
            && recall.status().equals("STOPPED") && recall.stop() != null && SubmissionJson.parse(recall.stop()).equals(stop));
        requireSame(pack != null && pack.id().equals(command.packageId()) && pack.hash().equals(command.payloadHash())
            && pack.captureHigh() == command.captureHigh() && pack.auditHigh() == command.auditHigh()
            && number(stop, "actualCount") == pack.count() && number(stop, "actualWeightGrams") == pack.grams());
    }

    private void bodyBinding(HandoverStopStore.Origin origin, HandoverStopCommand command, RecallStore.Package pack, JsonNode body) {
        requireSame(uuid(body, "recoveryPackageId").equals(pack.id()) && uuid(body, "changeRequestId").equals(command.recallId())
            && uuid(body, "assignmentId").equals(origin.assignment()) && uuid(body, "orderId").equals(origin.order())
            && uuid(body, "freezeDeviceId").equals(origin.device()) && text(body, "sourceRecoveryEpoch").equals(origin.epoch())
            && number(body, "workRound") == origin.round() && number(body, "captureHighWatermark") == command.captureHigh()
            && number(body, "expectedCount") == pack.count() && number(body, "expectedWeightGrams") == pack.grams());
        var coverage = array(body, "auditCoverage");
        requireSame(coverage.size() == 1 && uuid(coverage.get(0), "originDeviceId").equals(origin.device())
            && number(coverage.get(0), "throughSeq") == command.auditHigh());
        // The stop signer validated full captures/scans/events and audit coverage before signing this exact file hash.
        // RecoveryEvidence.validate also binds audit origins, so consumers must not call it or stop/acknowledge again.
    }

    private JsonNode read(String hash) {
        try { return SubmissionJson.parse(files.read(hash)); }
        catch (java.io.UncheckedIOException unavailable) {
            throw new BusinessException(409, "HANDOVER_STOP_FILE_UNAVAILABLE", "原恢复包不可读取或摘要不符，请保留资料");
        }
    }

    private void requireSame(boolean valid) {
        if (!valid) throw new BusinessException(409, "HANDOVER_EVIDENCE_CHANGED", "交接停止证据或恢复包已变化，请重新核对");
    }

    record Verified(JsonNode receipt, JsonNode body) {
        Verified { receipt = receipt.deepCopy(); body = body.deepCopy(); }
        @Override public JsonNode receipt() { return receipt.deepCopy(); }
        @Override public JsonNode body() { return body.deepCopy(); }
    }
}
