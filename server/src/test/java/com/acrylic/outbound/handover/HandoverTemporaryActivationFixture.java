package com.acrylic.outbound.handover;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

final class HandoverTemporaryActivationFixture {
    final UUID kase = UUID.randomUUID(), scope = UUID.randomUUID(), submission = UUID.randomUUID(), canonical = UUID.randomUUID();
    final UUID authorization = UUID.randomUUID(), client = UUID.randomUUID(), evidence = UUID.randomUUID();
    final UUID targetAuth = UUID.randomUUID(), device = UUID.randomUUID(), user = UUID.randomUUID();
    final ObjectNode body = object().put("authorizationId", authorization.toString()).put("sourceRecoveryEpoch", "1");
    final ObjectNode stop = object().put("sourceEvidenceSetId", evidence.toString());
    final ObjectNode prepared = object().put("caseId", kase.toString());
    final ObjectNode facts = object().put("caseId", kase.toString()).put("scopeId", scope.toString())
        .put("submissionId", submission.toString()).put("canonicalOrderId", canonical.toString())
        .put("targetDeviceId", device.toString()).put("targetUserId", user.toString())
        .put("fromWorkRound", "7").put("toWorkRound", "8").put("inputHighWatermark", "29");

    HandoverTemporaryActivationFixture() {
        body.putObject("source").put("clientOrderId", client.toString());
        facts.put("preparedTicketSha256", sha(prepared));
    }

    HandoverTemporaryActivationTicketStore.Ticket ticket() {
        var provisional = ticketWithPrefix("");
        return ticketWithPrefix(HandoverTemporaryActivationTicketStore.prefixSha(provisional));
    }

    HandoverTemporaryActivationTicketStore.Ticket ticketWithPrefix(String prefix) {
        return new HandoverTemporaryActivationTicketStore.Ticket(UUID.randomUUID(), kase, scope, submission, canonical,
            authorization, client, evidence, 1, 7, 8, 29, sha(prepared), sha(body), "b".repeat(64), prefix,
            targetAuth, device, user, 1, 2, HandoverReceiptCodec.bytes(prepared));
    }

    ObjectNode command() {
        var command = object().put("commandVersion", 1).put("commandType", "ACTIVATE").put("commandId", UUID.randomUUID().toString())
            .put("warehouseId", UUID.randomUUID().toString()).put("caseId", kase.toString()).put("transportRecoveryEpoch", "1")
            .put("targetDeviceId", device.toString()).put("targetUserId", user.toString());
        command.putObject("expected"); return command;
    }
}
