package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/** TEMP effects join the existing outer command/receipt/audit transaction. */
final class HandoverTemporaryActivationOperation implements HandoverCommand.Operation {
    private final HandoverStore store;
    private final HandoverTemporaryActivation activation;
    private final HandoverTemporaryRoundStore rounds;

    HandoverTemporaryActivationOperation(HandoverStore store, HandoverTemporaryActivation activation, HandoverTemporaryRoundStore rounds) {
        this.store = store; this.activation = activation; this.rounds = rounds;
    }
    @Override public String commandType() { return "ACTIVATE"; }

    @Override public JsonNode execute(HandoverCommand.Write write) {
        var facts = write.caseFacts();
        if (!"TARGET_PREPARED".equals(facts.path("status").asText()))
            throw new BusinessException(409, "HANDOVER_STATE_INVALID", "只有已准备接收机的案件可以激活");
        JsonNode decision = activation.activate(write);
        rounds.advance(facts);
        ObjectNode evidence = (ObjectNode) decision.deepCopy();
        evidence.remove(List.of("status", "retainedCount"));
        evidence.put("activatedAt", write.recordedAt().toString());
        store.advanceCase(write.caseId(), write.caseVersion(), "ACTIVATED", null, null, null, null, null, null,
            SubmissionJson.uuid(decision, "targetDeviceId"), SubmissionJson.uuid(decision, "targetUserId"), null,
            HandoverReceiptCodec.bytes(evidence), SubmissionJson.uuid(write.actor(), "principalId"), write.recordedAt());
        rounds.closeCase(write.caseId());
        outbox(write, decision);
        return decision;
    }

    private void outbox(HandoverCommand.Write write, JsonNode decision) {
        var order = SubmissionJson.uuid(write.caseFacts(), "canonicalOrderId"); long epoch = store.serverRecoveryEpoch();
        ObjectNode payload = (ObjectNode) decision.deepCopy();
        payload.remove(List.of("authorization", "targetSessionId"));
        payload.put("orderId", order.toString()).put("status", "CONTINUING").put("activatedAt", write.recordedAt().toString());
        store.insertOutbox(order, epoch, store.nextOutboxEntityVersion(order, epoch), "HANDOVER_TEMP_ACTIVATED",
            SubmissionJson.canonical(payload), write.recordedAt());
    }
}
