package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Verifies each historical handover's own signed STOP before the iterative history consumer validates its body. */
@Component
final class TemporaryHandoverPredecessor {
    private final HandoverStopStore stops;
    private final HandoverStopReceipts receipts;
    private final HandoverTemporaryOriginal originals;
    private final HandoverFaultArchive faults;

    TemporaryHandoverPredecessor(HandoverStopStore stops, HandoverStopReceipts receipts,
            HandoverTemporaryOriginal originals, HandoverFaultArchive faults) {
        this.stops = stops; this.receipts = receipts; this.originals = originals; this.faults = faults;
    }

    JsonNode read(TemporaryHandoverIntakeFacts.Facts facts) {
        var ticket = facts.ticket(); var origin = stops.origin(ticket.caseId());
        if ("FAULT_FENCED".equals(text(facts.caseFacts(), "stopState"))) {
            var body = faults.verify(facts.caseFacts(), origin, ticket.inputHighWatermark()).body();
            equal(sha(body), ticket.payloadSha(), "故障交接原正文摘要不符");
            return body;
        }
        var stop = stops.original(ticket.caseId()); var pack = stops.recovery(ticket.caseId());
        if (stop == null || pack == null)
            throw new BusinessException(409, "HANDOVER_STOP_NOT_PROVEN", "缺少原机签名停止证明或恢复包证据");
        equal(origin.submission(), ticket.submissionId(), "交接前驱停止批次不符");
        equal(origin.scope(), ticket.scopeId(), "交接前驱停止范围不符");
        require(origin.temporary() != null, "交接历史须保持TEMP来源");
        var receipt = receipts.verifyEvidence(stop, pack, origin, facts.caseFacts());
        var bound = receipt.path("originalCommand");
        var command = HandoverStopCommand.parse(ticket.caseId(), text(bound, "transportRecoveryEpoch"),
            HandoverReceiptCodec.bytes(bound.path("command")));
        HandoverApprovalEvidence.caseBinding(facts.caseFacts(), receipt, command, ticket.inputHighWatermark());
        var body = originals.read(command, origin);
        equal(sha(body), ticket.payloadSha(), "交接原正文摘要不符");
        return body;
    }
}
