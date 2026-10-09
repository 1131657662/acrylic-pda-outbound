package com.acrylic.outbound.handover;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Re-proves original archive bytes and origins. It never calls the original PDA or fabricates its STOP/ACK. */
@Component
final class HandoverFaultArchive {
    private final HandoverTemporaryOriginal originals;
    private final HandoverFaultStore store;
    private final HandoverReadReceipt receipts;
    private final HandoverAttachmentConsumer attachments;
    private final HandoverFormalOriginal formal;

    HandoverFaultArchive(HandoverTemporaryOriginal originals, HandoverFaultStore store, HandoverReadReceipt receipts,
            HandoverAttachmentConsumer attachments, HandoverFormalOriginal formal) {
        this.originals = originals;
        this.store = store; this.receipts = receipts;
        this.attachments = attachments;
        this.formal = formal;
    }

    JsonNode read(HandoverFaultInput input, HandoverStopStore.Origin origin) {
        if (input.formalArchive() != null) return formal.read(input, origin);
        require(origin.temporary() != null);
        store.requireArchiveCutoff(input);
        return originals.read(input.archive(), origin);
    }

    HandoverApprovalEvidence.Verified verify(JsonNode facts, HandoverStopStore.Origin origin, long high) {
        var recorded = receipts.verify(store.command(uuid(facts, "caseId")));
        attachments.verify(recorded);
        var input = HandoverFaultInput.read(recorded.command(), java.time.Instant.parse(text(recorded.receipt(), "recordedAt")));
        require(high == input.inputHigh() && high == number(facts, "inputHighWatermark")
            && origin.device().equals(input.device()) && number(facts, "caseVersion") > number(recorded.command().path("expected"), "caseVersion"));
        for (String field : java.util.List.of("caseId", "warehouseId", "scopeId", "submissionId", "canonicalOrderId", "sourceKind", "fromWorkRound", "toWorkRound"))
            require(facts.path(field).equals(recorded.receipt().path("caseContext").path(field)));
        var decision = recorded.receipt().path("businessDecision");
        var body = read(input, origin);
        require("FAULT_FENCED".equals(text(decision, "status")) && "FAULT_FENCE".equals(text(decision, "stopKind"))
            && decision.path("archivedEvidence").equals(input.descriptor(origin))
            && sha(body).equals(text(decision, "payloadSha256")));
        return new HandoverApprovalEvidence.Verified(parse(recorded.bytes()), body);
    }

    private static void require(boolean valid) { if (!valid) throw HandoverTemporaryRoots.changed(); }
}
