package com.acrylic.outbound.handover;

import com.acrylic.outbound.pda.PdaAccess;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.acrylic.outbound.handover.HandoverCommand.corruptUnless;

/** Verified read for the named receiving PDA; this does not grant scanning or import rights. */
@Service
@ConditionalOnProperty(prefix="outbound.handover.commands", name="enabled", havingValue="true")
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class HandoverPdaEvidenceService {
    private final PdaAccess access;
    private final HandoverStore cases;
    private final HandoverPdaEvidenceStore commands;
    private final HandoverReadReceipt receipts;
    private final HandoverApprovalEvidence evidence;
    private final HandoverChecklist checklist;

    HandoverPdaEvidenceService(PdaAccess access, HandoverStore cases, HandoverPdaEvidenceStore commands,
            HandoverReadReceipt receipts, HandoverApprovalEvidence evidence, HandoverChecklist checklist) {
        this.access = access; this.cases = cases; this.commands = commands;
        this.receipts = receipts; this.evidence = evidence; this.checklist = checklist;
    }

    public JsonNode manifest(UUID caseId) {
        var principal = access.require("pda:scan");
        principal = access.require("pda:submit");
        var facts = cases.caseFacts(caseId);
        requireTarget(principal, facts);
        var approved = verified(caseId, "APPROVE");
        var prepared = verified(caseId, "PREPARE");
        var activated = "ACTIVATED".equals(facts.path("status").asText()) ? verified(caseId, "ACTIVATE") : null;
        var approval = approved.command();
        requireApproval(facts, approved);
        requireActivation(facts, activated);
        var stop = evidence.verify(facts, SubmissionJson.number(approval, "inputHighWatermark"));
        var lines = approval.path("checklist");
        checklist.dispositions(stop.body(), lines);
        checklist.compareStored(cases.checklist(caseId), lines, true);
        var ticket = ticket(facts, prepared);
        var recoveryHash = stop.receipt().path("originalCommand").path("command").path("payloadSha256").asText();
        corruptUnless(recoveryHash.matches("[a-f0-9]{64}"));
        var result = SubmissionJson.object().put("caseId", caseId.toString())
            .put("status", facts.path("status").asText()).put("recoveryPackageSha256", recoveryHash)
            .put("inputHighWatermark", facts.path("inputHighWatermark").asText())
            .put("preparedTicketSha256", facts.path("preparedTicketSha256").asText());
        result.set("preparedTicket", ticket);
        result.set("checklist", lines.deepCopy());
        result.set("stopReceipt", stop.receipt());
        result.set("approvalReceipt", SubmissionJson.parse(approved.bytes()));
        result.set("preparationReceipt", SubmissionJson.parse(prepared.bytes()));
        if (activated != null) result.set("activationReceipt", SubmissionJson.parse(activated.bytes()));
        result.set("capabilities", HandoverReceiptCodec.capabilities());
        requireTarget(access.current(), cases.caseFacts(caseId));
        return result;
    }

    private HandoverReadReceipt.Verified verified(UUID caseId, String kind) {
        var result = receipts.verify(commands.command(caseId, kind));
        corruptUnless(kind.equals(result.command().path("commandType").asText())
            && caseId.toString().equals(result.command().path("caseId").asText()));
        return result;
    }

    private void requireTarget(PdaSessions.Principal principal, JsonNode facts) {
        if (!List.of("TARGET_PREPARED", "ACTIVATED").contains(facts.path("status").asText())
                || !principal.warehouseId().toString().equals(facts.path("warehouseId").asText())
                || !principal.deviceId().toString().equals(facts.path("targetDeviceId").asText())
                || !principal.userId().toString().equals(facts.path("targetUserId").asText()))
            throw HandoverCommand.missing();
    }

    private void requireApproval(JsonNode facts, HandoverReadReceipt.Verified approved) {
        var command = approved.command(); var receipt = approved.receipt();
        corruptUnless("APPROVED".equals(receipt.path("businessDecision").path("status").asText())
            && receipt.path("actor").path("principalId").equals(facts.path("approvedBy")));
        for (String field : List.of("inputHighWatermark", "stopState", "integrityState", "originalResultState", "physicalState"))
            corruptUnless(command.path(field).equals(facts.path(field)));
    }

    private void requireActivation(JsonNode facts, HandoverReadReceipt.Verified activated) {
        if (activated == null) return;
        var command = activated.command(); var receipt = activated.receipt();
        var decision = receipt.path("businessDecision");
        corruptUnless("ACTIVATED".equals(decision.path("status").asText())
            && decision.path("caseId").equals(facts.path("caseId"))
            && command.path("targetDeviceId").equals(facts.path("targetDeviceId"))
            && command.path("targetUserId").equals(facts.path("targetUserId"))
            && decision.path("targetDeviceId").equals(facts.path("targetDeviceId"))
            && decision.path("targetUserId").equals(facts.path("targetUserId")));
        var activation = ((ObjectNode) decision.deepCopy());
        activation.remove(List.of("status", "retainedReservations"));
        activation.put("activatedAt", receipt.path("recordedAt").asText());
        corruptUnless(HandoverCommand.sha(HandoverReceiptCodec.bytes(activation))
            .equals(facts.path("activationSha256").asText()));
    }

    private JsonNode ticket(JsonNode facts, HandoverReadReceipt.Verified prepared) {
        var command = prepared.command(); var receipt = prepared.receipt();
        corruptUnless("TARGET_PREPARED".equals(receipt.path("businessDecision").path("status").asText())
            && command.path("targetDeviceId").equals(facts.path("targetDeviceId"))
            && command.path("targetUserId").equals(facts.path("targetUserId"))
            && receipt.path("businessDecision").path("preparedTicketSha256").equals(facts.path("preparedTicketSha256")));
        var ticket = SubmissionJson.object().put("caseId", facts.path("caseId").asText())
            .put("targetDeviceId", facts.path("targetDeviceId").asText())
            .put("targetUserId", facts.path("targetUserId").asText())
            .put("inputHighWatermark", facts.path("inputHighWatermark").asText())
            .put("canonicalOrderId", facts.path("canonicalOrderId").asText())
            .put("issuedAt", receipt.path("recordedAt").asText());
        corruptUnless(HandoverCommand.sha(HandoverReceiptCodec.bytes(ticket))
            .equals(facts.path("preparedTicketSha256").asText()));
        return ticket;
    }
}
