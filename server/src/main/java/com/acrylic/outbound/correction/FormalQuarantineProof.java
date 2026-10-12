package com.acrylic.outbound.correction;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/** The unsigned original physical approval is explicitly combined with signed recovery/application evidence. */
@Component
final class FormalQuarantineProof {
    private final FormalQuarantineRecovery recoveries;
    private final FormalQuarantineApplication applications;
    private final FormalQuarantineApproval approvals;
    FormalQuarantineProof(FormalQuarantineRecovery recoveries,FormalQuarantineApplication applications,FormalQuarantineApproval approvals) {
        this.recoveries=recoveries; this.applications=applications;this.approvals=approvals;
    }
    ObjectNode read(JsonNode origin) {
        try {
            var recovery=recoveries.read(origin); var application=applications.read(origin,recovery);
            var result=SubmissionJson.object().put("proofType","FORMAL_RECOVERY_APPLICATION_ORIGINALS").put("receiptId",origin.path("dispositionId").asText())
                .put("recordedAt",origin.path("approvedAt").asText());
            result.putObject("actor").put("principalId",origin.path("approvedBy").asText());
            result.set("physicalApproval",approvals.read(origin,recovery)); result.set("recovery",recovery); result.set("application",application); return result;
        } catch(RuntimeException unavailable) { throw CorrectionCommand.corrupt(); }
    }
}
