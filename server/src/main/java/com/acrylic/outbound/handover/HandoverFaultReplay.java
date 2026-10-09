package com.acrylic.outbound.handover;

import java.util.Arrays;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Replays a fault receipt only while its original archive and durable device fence remain verifiable. */
@Component
final class HandoverFaultReplay {
    private final HandoverStore cases;
    private final HandoverStopStore origins;
    private final HandoverFaultArchive archives;

    HandoverFaultReplay(HandoverStore cases, HandoverStopStore origins, HandoverFaultArchive archives) {
        this.cases = cases; this.origins = origins; this.archives = archives;
    }

    void verify(HandoverReadReceipt.Verified recorded) {
        var caseId = uuid(recorded.command(), "caseId");
        var facts = cases.caseFacts(caseId);
        HandoverCommand.corruptUnless("FAULT_FENCED".equals(text(facts, "stopState"))
            && "COMPLETE".equals(text(facts, "integrityState")));
        var verified = archives.verify(facts, origins.origin(caseId), number(facts, "inputHighWatermark"));
        HandoverCommand.corruptUnless(Arrays.equals(recorded.bytes(), HandoverReceiptCodec.bytes(verified.receipt())));
    }
}
