package com.acrylic.outbound.correction;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Maker-checker approval publishes result effects separately from validated actual accounting. */
final class CorrectionApprove implements CorrectionCommand.Operation {
    private final CorrectionStore store;
    private final CorrectionResultSnapshots snapshots;
    private final CorrectionApprovalEffects effects;
    private final CorrectionHeaderEvidence headers;
    CorrectionApprove(CorrectionStore store, CorrectionAccounting accounting, CorrectionHeaderEvidence headers) {
        this.store = store; this.headers = headers; snapshots = new CorrectionResultSnapshots(store); effects = new CorrectionApprovalEffects(store, accounting);
    }
    @Override public String commandType() { return "APPROVE"; }

    @Override public JsonNode execute(CorrectionCommand.Write write) {
        validate(write);
        var current = store.lockCurrentVersion(write.orderId());
        long expected = write.anchor().path("expectedResultVersion").asLong(), version = expected + 1;
        if (CorrectionResultSnapshots.number(current, "result_version") != expected
            || store.resultVersionExists(write.orderId(), version) != 0) throw CorrectionCommand.stale();
        var lines = store.lines(write.documentId());
        var dispatches = store.dispatchesForUpdate(write.orderId());
        store.lockBalesAndCommitments(dispatches);
        var result = result(write, current, lines, version);
        snapshots.version(write, current, result);
        var applied = effects.apply(write, version, lines); result.setAll(applied);
        snapshots.scans(write, version, lines, false);
        store.moveCurrentPointer(write.orderId(), version, write.recordedAt());
        if (store.effectDocument(write.documentId(), version) != 1) throw CorrectionCommand.stale();
        snapshots.outbox(write, result, applied.path("reopenedCycles").isEmpty() ? "CORRECTION_EFFECTIVE" : "CYCLE_REOPENED");
        result.remove("headerSnapshot"); result.put("scanCoverage", Integer.toString(store.dispatchesForUpdate(write.orderId()).size()));
        return result;
    }

    private void validate(CorrectionCommand.Write write) {
        if (write.documentId() == null) throw CorrectionCommand.missing();
        String actor = write.actor().path("principalId").asText();
        if (actor.equals(write.anchor().path("requestedBy").asText())) throw CorrectionCommand.selfApproval();
        if (!"SUBMITTED".equals(write.anchor().path("status").asText())) throw CorrectionCommand.stale();
        if (store.approveDocument(write.documentId(), UUID.fromString(actor), write.recordedAt()) != 1) throw CorrectionCommand.stale();
    }

    private ObjectNode result(CorrectionCommand.Write write, Map<String, Object> current, List<Map<String, Object>> lines, long version) {
        long[] totals = snapshots.totals(current, lines, false);
        String header = current.get("header_snapshot").toString();
        boolean headerCorrection = "HEADER_CORRECTION".equals(write.anchor().path("type").asText());
        var before = headerCorrection ? headers.normalize(write.orderId(), SubmissionJson.parse(header)) : SubmissionJson.parse(header);
        if (headerCorrection) header = CorrectionHeader.merge(SubmissionJson.canonical(before), headers.createPatch(write));
        var result = SubmissionJson.object().put("documentId", write.documentId().toString()).put("status", "EFFECTIVE")
            .put("approvedBy", write.actor().path("principalId").asText()).put("approvedAt", write.recordedAt().toString())
            .put("documentSha256", write.anchor().path("documentSha256").asText()).put("orderId", write.orderId().toString())
            .put("previousResultVersion", write.anchor().path("expectedResultVersion").asText()).put("resultVersion", Long.toString(version))
            .put("type", write.anchor().path("type").asText()).put("effectiveCount", Long.toString(totals[0]))
            .put("effectiveWeightG", Long.toString(totals[1])).put("headerSnapshot", header);
        if (headerCorrection) result.set("headerProof", CorrectionHeaderEvidence.approvalHashes(
            before, SubmissionJson.parse(header)));
        return result;
    }
}
