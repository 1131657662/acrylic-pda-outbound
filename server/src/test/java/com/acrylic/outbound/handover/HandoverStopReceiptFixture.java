package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Synthetic signed protocol fixture; it is not device or physical evidence. */
final class HandoverStopReceiptFixture {
    final UUID kase = UUID.randomUUID(), receiptId = UUID.randomUUID(), packageEvidenceId = UUID.randomUUID();
    final Instant now = Instant.parse("2026-10-03T00:00:00.123Z");
    final HandoverStopAccess.Context context;
    final HandoverStopCommand command;
    final ObjectNode recall;
    final Map<String, Object> stop, pack;

    HandoverStopReceiptFixture(OfflineSigner signer) {
        var origin = new HandoverStopStore.Origin(id(), id(), id(), id(), id(), id(), id(), "1", 1);
        var actor = new PdaSessions.Principal(id(), origin.operator(), "fixture", origin.device(), origin.warehouse(), "1", "1");
        context = new HandoverStopAccess.Context(origin, actor, SubmissionJson.object().put("caseId", kase.toString()).put("caseVersion", "1"));
        var body = SubmissionJson.object().put("commandId", id().toString()).put("expectedCaseVersion", "1")
            .put("changeRequestId", id().toString()).put("recoveryPackageId", id().toString()).put("payloadSha256", "a".repeat(64))
            .put("captureHighWatermark", "2").put("auditThroughSeq", "8");
        command = HandoverStopCommand.parse(kase, "1", HandoverReceiptCodec.bytes(body));
        recall = command.acknowledgement().put("status", "STOPPED").put("changeRequestId", command.recallId().toString())
            .put("orderId", origin.order().toString()).put("assignmentId", origin.assignment().toString()).put("issuedRecoveryEpoch", "1")
            .put("mayResumeScanning", false).put("stockReleased", false).put("actualCount", "2").put("actualWeightGrams", "10");
        signRecall(signer, "acrylic-pda-recall");
        byte[] bytes = new HandoverStopReceipts(new HandoverStopSignature(signer)).sign(command, context, recall, receiptId, packageEvidenceId, now);
        stop = row("STOP_PROOF", receiptId, HandoverCommand.sha(bytes), bytes);
        pack = row("RECOVERY_PACKAGE", packageEvidenceId, command.payloadHash(), bytes);
    }

    void signRecall(OfflineSigner signer, String audience) {
        recall.remove("signedReceipt");
        recall.put("signedReceipt", signer.sign(Map.of("iss", "acrylic-outbound", "aud", audience, "receipt", recall.deepCopy())).token());
    }

    private Map<String, Object> row(String kind, UUID id, String hash, byte[] bytes) {
        var row = new HashMap<String, Object>(); row.put("id", Ids.bytes(id)); row.put("case_id", Ids.bytes(kase));
        row.put("device_id", Ids.bytes(context.origin().device())); row.put("kind", kind);
        row.put("payload_sha256", HexFormat.of().parseHex(hash)); row.put("high_watermark", 2L);
        row.put("recorded_at", Timestamp.from(now)); row.put("receipt_bytes", bytes); return row;
    }
    private UUID id() { return UUID.randomUUID(); }
}
