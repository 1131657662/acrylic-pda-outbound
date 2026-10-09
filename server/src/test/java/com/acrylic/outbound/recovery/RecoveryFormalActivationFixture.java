package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/** Synthetic original unsigned ACK/assignment/audit facts in real pinned files, with development preparation RSA. */
final class RecoveryFormalActivationFixture {
    final RecoveryPreparationFixture prep = new RecoveryPreparationFixture("FORMAL");
    final ObjectNode command;
    final ObjectNode receipt;
    final ObjectNode assignment;
    final ObjectNode event;

    RecoveryFormalActivationFixture() throws Exception {
        var id = prep.state.authority.source.identity; var manifest = prep.manifest;
        command = SubmissionJson.object().put("bundleId", prep.receipt.path("bundleId").asText())
            .put("manifestSha256", prep.receipt.path("manifestSha256").asText()).put("assignmentEpoch", "1")
            .put("inventorySnapshotId", manifest.path("inventorySnapshotId").asText()).put("activatedAt", "2026-09-20T00:02:00.123456789Z");
        receipt = SubmissionJson.object().put("assignmentId", id.qualificationId().toString())
            .put("bundleId", prep.receipt.path("bundleId").asText()).put("downloadAckAt", "2026-09-20T00:03:00.456Z");
        assignment = SubmissionJson.object().put("id", id.qualificationId().toString()).put("orderId", id.canonicalOrderId().toString())
            .put("warehouseId", id.warehouseId().toString()).put("deviceId", id.deviceId().toString()).put("operatorId", id.operatorId().toString())
            .put("epoch", "1").put("serverRecoveryEpoch", id.sourceRecoveryEpoch()).put("inventorySnapshotId", manifest.path("inventorySnapshotId").asText())
            .put("revision", 1).put("offlineAuthorizationId", prep.state.authority.claims.path("jti").asText()).put("authorizationSequence", "0")
            .put("issuedAt", "2026-09-20T00:00:00Z").put("expiresAt", "2026-09-21T00:00:00Z");
        event = SubmissionJson.object().put("id", "1").put("actorId", id.operatorId().toString()).put("eventType", "ORDER_DOWNLOAD_ACK")
            .put("entityType", "ORDER").put("entityId", id.canonicalOrderId().toString()).put("serverTime", "2026-09-20T00:03:00.123Z")
            .put("requestId", "original-request-1"); bindCommand(); write();
    }

    void bindCommand() { event.put("details", command.toString()); }

    void write() throws Exception {
        assignment.set("downloadAckAt", receipt.path("downloadAckAt"));
        var activation = prep.origin.putObject("activation").put("command", command.toString()).put("receipt", receipt.toString());
        activation.set("assignment", assignment); activation.set("operationEvent", event); prep.write();
    }

    RecoverySourceFormalActivation reader() {
        return new RecoverySourceFormalActivation(new RecoveryReceiptKeys(List.of(prep.state.authority.key.pair.getPublic().getEncoded())));
    }
}
