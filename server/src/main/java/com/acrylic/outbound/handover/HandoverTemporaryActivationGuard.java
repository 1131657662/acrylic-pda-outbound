package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.pda.PdaSessionLocks;
import com.acrylic.outbound.pda.PdaSessions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Re-proves the prepared recipient and original confirmation before ticket consumption. */
@Component
final class HandoverTemporaryActivationGuard {
    private final PdaSessionLocks locks;
    private final PdaSessions sessions;
    private final HandoverTemporaryConfirmationReceipt receipts;
    private final HandoverTemporaryTarget targets;
    private final HandoverApprovalEvidence evidence;

    HandoverTemporaryActivationGuard(PdaSessionLocks locks, PdaSessions sessions,
            HandoverTemporaryConfirmationReceipt receipts, HandoverTemporaryTarget targets, HandoverApprovalEvidence evidence) {
        this.locks = locks; this.sessions = sessions; this.receipts = receipts; this.targets = targets; this.evidence = evidence;
    }

    PdaSessions.Principal require(HandoverCommand.Write write, HandoverTemporaryActivationTicketStore.Ticket ticket,
            Map<String, Object> row) {
        requireInput(write.command().body(), ticket);
        if (row == null || !(row.get("session_id") instanceof String id)) throw conflict("HANDOVER_TEMP_CONFIRMATION_REQUIRED");
        var expected = new PdaSessions.Principal(UUID.fromString(id), ticket.targetUserId(), "handover", ticket.targetDeviceId(),
            uuid(write.caseFacts(), "warehouseId"), Long.toString(ticket.targetUserAuthVersion()), Long.toString(ticket.targetDeviceVersion()));
        locks.principal(expected);
        var actor = sessions.recheck(expected);
        if (!actor.userId().equals(expected.userId()) || !actor.deviceId().equals(expected.deviceId())
                || !actor.warehouseId().equals(expected.warehouseId()) || !actor.userAuthVersion().equals(expected.userAuthVersion())
                || !actor.deviceVersion().equals(expected.deviceVersion())) throw conflict("HANDOVER_TEMP_TARGET_SESSION_CHANGED");
        var confirmation = receipts.verifyForActivation(row, actor);
        var verified = evidence.verify(write.caseFacts(), ticket.inputHighWatermark());
        var body = verified.body();
        requireSource(ticket, body, verified.receipt().path("temporaryStop"));
        var prepared = parse(ticket.ticketBytes());
        ObjectNode command = (ObjectNode) write.command().body();
        command.put("commandType", "PREPARE").put("targetAuthorizationId", ticket.targetAuthorizationId().toString());
        var qualification = targets.require(command, write.caseFacts(), body);
        if (!qualification.equals(prepared.path("temporaryQualification")) || !prepared.equals(confirmation.path("preparedTicket")))
            throw conflict("HANDOVER_TEMP_CONFIRMATION_CHANGED");
        requireConfirmation(ticket, confirmation);
        return actor;
    }

    static void requireInput(JsonNode command, HandoverTemporaryActivationTicketStore.Ticket ticket) {
        var allowed = Set.of("commandVersion", "commandType", "commandId", "warehouseId", "caseId", "transportRecoveryEpoch",
            "targetDeviceId", "targetUserId", "expected", "reason");
        var fields = new HashSet<String>(); command.fieldNames().forEachRemaining(fields::add);
        var required = new HashSet<>(allowed); required.remove("reason");
        if (!allowed.containsAll(fields) || !fields.containsAll(required) || !command.path("commandVersion").isInt()
                || command.path("commandVersion").intValue() != 1 || !"ACTIVATE".equals(command.path("commandType").asText()))
            throw new BusinessException(400, "INVALID_HANDOVER_COMMAND", "临时交接激活命令字段无效");
        if (!ticket.caseId().equals(uuid(command, "caseId")) || !ticket.targetDeviceId().equals(uuid(command, "targetDeviceId"))
                || !ticket.targetUserId().equals(uuid(command, "targetUserId"))) throw conflict("HANDOVER_CONTEXT_CHANGED");
    }

    private void requireConfirmation(HandoverTemporaryActivationTicketStore.Ticket ticket, JsonNode receipt) {
        var original = receipt.path("originalCommand").path("command");
        if (!ticket.targetAuthorizationId().toString().equals(original.path("targetAuthorizationId").asText())
                || !ticket.payloadSha().equals(original.path("payloadSha256").asText())
                || !ticket.checklistSha().equals(original.path("checklistSha256").asText())
                || !ticket.preparedTicketSha().equals(original.path("preparedTicketSha256").asText()))
            throw conflict("HANDOVER_TEMP_CONFIRMATION_CHANGED");
    }

    static void requireSource(HandoverTemporaryActivationTicketStore.Ticket ticket, JsonNode body, JsonNode stop) {
        if (!ticket.payloadSha().equals(sha(body)) || !ticket.sourceAuthorizationId().equals(uuid(body, "authorizationId"))
                || !ticket.sourceClientOrderId().equals(uuid(body.path("source"), "clientOrderId"))
                || ticket.sourceRecoveryEpoch() != number(body, "sourceRecoveryEpoch")
                || !ticket.sourceEvidenceSetId().equals(uuid(stop, "sourceEvidenceSetId")))
            throw conflict("HANDOVER_TEMP_SOURCE_CHANGED");
    }

    private static BusinessException conflict(String code) { return new BusinessException(409, code, "临时交接接收人与原确认不一致，请保留原件"); }
}
