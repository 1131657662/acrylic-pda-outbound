package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** Prepares a named recipient; the immutable signed result binds the TEMP credential when applicable. */
final class HandoverPreparation implements HandoverCommand.Operation {
    private final HandoverStore store;
    private final HandoverTargetQualification targets;
    private final HandoverTemporaryTarget temporary;
    private final HandoverApprovalEvidence evidence;

    HandoverPreparation(HandoverStore store, HandoverTargetQualification targets,
            HandoverTemporaryTarget temporary, HandoverApprovalEvidence evidence) {
        this.store = store; this.targets = targets; this.temporary = temporary; this.evidence = evidence;
    }

    @Override public String commandType() { return "PREPARE"; }

    @Override public JsonNode execute(HandoverCommand.Write write) {
        var input = write.command(); var facts = write.caseFacts();
        if (!facts.path("status").asText().equals("APPROVED"))
            throw new BusinessException(409, "HANDOVER_STATE_INVALID", "只有已批准的案件可以准备接收机");
        UUID device = input.uuid("targetDeviceId"), user = input.uuid("targetUserId");
        targets.require(device, user, SubmissionJson.uuid(facts, "warehouseId"));
        var ticket = SubmissionJson.object().put("caseId", write.caseId().toString())
            .put("targetDeviceId", device.toString()).put("targetUserId", user.toString())
            .put("inputHighWatermark", facts.path("inputHighWatermark").asText())
            .put("canonicalOrderId", facts.path("canonicalOrderId").asText()).put("issuedAt", write.recordedAt().toString());
        if ("TEMP".equals(facts.path("sourceKind").asText())) {
            var body = evidence.verify(facts, SubmissionJson.number(facts, "inputHighWatermark")).body();
            ticket.set("temporaryQualification", temporary.require(input.body(), facts, body));
        } else if (input.body().has("targetAuthorizationId")) throw new BusinessException(400,
            "INVALID_HANDOVER_COMMAND", "正式来源不使用临时授权");
        byte[] bytes = HandoverReceiptCodec.bytes(ticket);
        store.advanceCase(write.caseId(), write.caseVersion(), "TARGET_PREPARED", null, null, null,
            null, null, null, device, user, bytes, null, SubmissionJson.uuid(write.actor(), "principalId"), write.recordedAt());
        var decision = SubmissionJson.object().put("caseId", write.caseId().toString()).put("status", "TARGET_PREPARED")
            .put("targetDeviceId", device.toString()).put("targetUserId", user.toString())
            .put("preparedTicketSha256", HandoverCommand.sha(bytes));
        if (ticket.has("temporaryQualification")) decision.set("temporaryQualification", ticket.path("temporaryQualification"));
        return decision;
    }
}
