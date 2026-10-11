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
    private final HandoverTemporaryActivationTicketStore tickets;
    private final com.acrylic.outbound.recovery.RecoveryTemporaryQualification recovered;

    HandoverPreparation(HandoverStore store, HandoverTargetQualification targets,
            HandoverTemporaryTarget temporary, HandoverApprovalEvidence evidence,
            HandoverTemporaryActivationTicketStore tickets) {
        this.store = store; this.targets = targets; this.temporary = temporary; this.evidence = evidence; this.tickets = tickets; recovered=null;
    }
    private HandoverPreparation(HandoverPreparation original,com.acrylic.outbound.recovery.RecoveryTemporaryQualification recovered) {
        store=original.store; targets=original.targets; temporary=original.temporary; evidence=original.evidence; tickets=original.tickets;
        this.recovered=java.util.Objects.requireNonNull(recovered);
    }
    HandoverPreparation recovered(com.acrylic.outbound.recovery.RecoveryTemporaryQualification qualification) {
        return new HandoverPreparation(this,qualification);
    }

    @Override public String commandType() { return "PREPARE"; }

    @Override public JsonNode execute(HandoverCommand.Write write) {
        var input = write.command(); var facts = write.caseFacts();
        if (!facts.path("status").asText().equals("APPROVED"))
            throw new BusinessException(409, "HANDOVER_STATE_INVALID", "只有已批准的案件可以准备接收机");
        UUID device = input.uuid("targetDeviceId"), user = input.uuid("targetUserId");
        if ("TEMP".equals(facts.path("sourceKind").asText())) targets.temporary(device, user, SubmissionJson.uuid(facts, "warehouseId"));
        else targets.require(device, user, SubmissionJson.uuid(facts, "warehouseId"));
        var ticket=ticket(write);
        if ("TEMP".equals(facts.path("sourceKind").asText())) prepareTemporary(write,ticket);
        else if (input.body().has("targetAuthorizationId")) throw new BusinessException(400,
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
    private com.fasterxml.jackson.databind.node.ObjectNode ticket(HandoverCommand.Write write) {
        var facts=write.caseFacts(); var input=write.command();
        return SubmissionJson.object().put("caseId", write.caseId().toString())
            .put("targetDeviceId", input.text("targetDeviceId")).put("targetUserId", input.text("targetUserId"))
            .put("inputHighWatermark", facts.path("inputHighWatermark").asText())
            .put("canonicalOrderId", facts.path("canonicalOrderId").asText()).put("issuedAt", write.recordedAt().toString());
    }
    private void prepareTemporary(HandoverCommand.Write write,com.fasterxml.jackson.databind.node.ObjectNode ticket) {
        var facts=write.caseFacts(); var input=write.command();
        var verified = evidence.verify(facts, SubmissionJson.number(facts, "inputHighWatermark")); var body = verified.body();
        if(recovered!=null) {
            var q=recovered.facts(); var recipient=q.path("recipient");
            HandoverCommand.corruptUnless(input.text("targetQualificationId").equals(q.path("qualificationId").asText())
                && input.text("targetDeviceId").equals(recipient.path("deviceId").asText())
                && input.text("targetUserId").equals(recipient.path("userId").asText())
                && facts.path("caseId").equals(q.path("caseId")) && facts.path("scopeId").equals(q.path("scopeId")));
            ticket.set("temporaryQualification",recovered.preparationDescriptor());
        } else {
            ticket.set("temporaryQualification", temporary.require(input.body(), facts, body));
            var archived = "FAULT_FENCED".equals(facts.path("stopState").asText())
                ? verified.receipt().path("businessDecision").path("archivedEvidence") : verified.receipt().path("temporaryStop");
            tickets.issue(new HandoverTemporaryActivationTicketStore.Preparation(facts, input.body(), ticket, body,
                archived, store.checklist(write.caseId()), write.recordedAt()));
        }
    }
}
