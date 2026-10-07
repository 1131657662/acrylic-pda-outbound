package com.acrylic.outbound.handover;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/** Appends the evidence pair, case transition, idempotency index, audit and outbox inside the caller's transaction. */
@Component
final class HandoverStopWriter {
    private final HandoverStopRecovery recovery;
    private final HandoverStopReceipts receipts;
    private final HandoverStore cases;
    private final IdempotencyRequests requests;
    private final AuditWriter audit;

    HandoverStopWriter(HandoverStopRecovery recovery, HandoverStopReceipts receipts,
            HandoverStore cases, IdempotencyRequests requests, AuditWriter audit) {
        this.recovery = recovery; this.receipts = receipts; this.cases = cases; this.requests = requests; this.audit = audit;
    }

    byte[] record(HandoverStopCommand command, HandoverStopAccess.Context context, String requestId) {
        var origin = context.origin(); var stop = recovery.stop(command, origin);
        var receiptId = Ids.next(); var packageId = Ids.next(); var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        byte[] receipt = receipts.sign(command, context, stop, receiptId, packageId, now);
        cases.insertEvidence(receiptId, command.caseId(), "STOP_PROOF", origin.device(), HandoverCommand.sha(receipt),
            command.captureHigh(), receipt, now);
        cases.insertEvidence(packageId, command.caseId(), "RECOVERY_PACKAGE", origin.device(), command.payloadHash(),
            command.captureHigh(), receipt, now);
        cases.advanceCase(command.caseId(), command.caseVersion(), "EVIDENCE_PENDING", null, command.captureHigh(),
            "DEVICE_ACKED", "COMPLETE", null, null, null, null, null, null, origin.operator(), now);
        requests.record(HandoverStopService.key(command, context), command.digest(), receiptId);
        var details = SubmissionJson.object().put("caseId", command.caseId().toString()).put("commandSha256", command.digest())
            .put("originalResponseSha256", HandoverCommand.sha(receipt));
        if (command.temporary() == null) details.put("recoveryPackageId", command.packageId().toString());
        else details.put("sourceEvidenceSetId", command.temporary().evidenceSet().toString());
        audit.append(new AuditWriter.Event(origin.operator(), "HANDOVER_STOPPED", "HANDOVER_STOP", receiptId, details.toString()), requestId);
        var payload = details.deepCopy().put("orderId", origin.order().toString()).put("scopeId", origin.scope().toString())
            .put("status", "RECALL_PENDING").put("caseStatus", "EVIDENCE_PENDING").put("receiptId", receiptId.toString());
        long epoch = cases.serverRecoveryEpoch();
        cases.insertOutbox(origin.order(), epoch, cases.nextOutboxEntityVersion(origin.order(), epoch), "HANDOVER_STOPPED", payload.toString(), now);
        return receipt;
    }
}
