package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.handover.HandoverCommand.corruptUnless;

/** Signed original command, source, actor and durable recovery descriptor; never a scan or release authorization. */
@Component
final class HandoverStopReceipts {
    private final HandoverStopSignature signatures;
    HandoverStopReceipts(HandoverStopSignature signatures) { this.signatures = signatures; }

    byte[] sign(HandoverStopCommand command, HandoverStopAccess.Context context, JsonNode recall,
            UUID receiptId, UUID packageEvidenceId, Instant now) {
        requireActor(context);
        verifyStop(recall, command, context.origin());
        var receipt = SubmissionJson.object().put("receiptVersion", 1).put("receiptId", receiptId.toString())
            .put("packageEvidenceId", packageEvidenceId.toString()).put("routeScope", HandoverStopService.ROUTE)
            .put("status", "STOPPED").put("commandSha256", command.digest()).put("recordedAt", now.toString());
        receipt.set("originalCommand", command.bound()); receipt.set("source", source(context.origin()));
        receipt.set("caseContext", context.facts()); receipt.set(command.temporary() == null ? "recallStop" : "temporaryStop", recall);
        var actor = context.actor();
        receipt.putObject("actor").put("principalId", actor.userId().toString()).put("deviceId", actor.deviceId().toString())
            .put("sessionId", actor.sessionId().toString()).put("userAuthVersion", actor.userAuthVersion());
        receipt.set("capabilities", HandoverReceiptCodec.capabilities());
        return HandoverReceiptCodec.bytes(signatures.sign(receipt));
    }

    JsonNode verify(Map<String, Object> stop, Map<String, Object> pack, HandoverStopAccess.Context context) {
        requireActor(context);
        return verifyEvidence(stop, pack, context.origin(), context.facts());
    }

    /** Supervisor consumers prove the original actor from the immutable source, without impersonating the PDA. */
    JsonNode verifyEvidence(Map<String, Object> stop, Map<String, Object> pack,
            HandoverStopStore.Origin origin, JsonNode facts) {
        try {
            corruptUnless(stop != null && pack != null);
            byte[] bytes = (byte[]) stop.get("receipt_bytes");
            var receipt = SubmissionJson.parse(bytes);
            corruptUnless(Arrays.equals(bytes, HandoverReceiptCodec.bytes(receipt))
                && Arrays.equals(bytes, (byte[]) pack.get("receipt_bytes")));
            signatures.verify(receipt);
            var bound = receipt.path("originalCommand");
            var command = HandoverStopCommand.parse(SubmissionJson.uuid(bound, "caseId"),
                SubmissionJson.text(bound, "transportRecoveryEpoch"), HandoverReceiptCodec.bytes(bound.path("command")));
            bind(receipt, command, origin, facts);
            verifyStop(receipt.path(command.temporary() == null ? "recallStop" : "temporaryStop"), command, origin);
            row(stop, "STOP_PROOF", SubmissionJson.uuid(receipt, "receiptId"), command, origin, HandoverCommand.sha(bytes), receipt);
            row(pack, "RECOVERY_PACKAGE", SubmissionJson.uuid(receipt, "packageEvidenceId"), command, origin, command.payloadHash(), receipt);
            return receipt;
        } catch (RuntimeException invalid) { throw HandoverCommand.corrupt(); }
    }

    private void bind(JsonNode receipt, HandoverStopCommand command, HandoverStopStore.Origin origin, JsonNode facts) {
        corruptUnless(receipt.path("receiptVersion").asInt() == 1 && receipt.path("status").asText().equals("STOPPED")
            && receipt.path("routeScope").asText().equals(HandoverStopService.ROUTE)
            && receipt.path("commandSha256").asText().equals(command.digest())
            && receipt.path("originalCommand").equals(command.bound())
            && receipt.path("capabilities").equals(HandoverReceiptCodec.capabilities())
            && receipt.path("source").equals(source(origin)) && command.epoch().equals(origin.epoch()));
        corruptUnless(command.caseId().toString().equals(facts.path("caseId").asText())
            && command.caseId().toString().equals(receipt.path("caseContext").path("caseId").asText())
            && Long.toString(command.caseVersion()).equals(receipt.path("caseContext").path("caseVersion").asText())
            && origin.operator().toString().equals(receipt.path("actor").path("principalId").asText())
            && origin.device().toString().equals(receipt.path("actor").path("deviceId").asText()));
    }

    private void requireActor(HandoverStopAccess.Context context) {
        corruptUnless(context.origin().operator().equals(context.actor().userId())
            && context.origin().device().equals(context.actor().deviceId())
            && context.origin().warehouse().equals(context.actor().warehouseId()));
    }

    private void verifyRecall(JsonNode recall, HandoverStopCommand command, HandoverStopStore.Origin origin) {
        signatures.verifyRecall(recall);
        corruptUnless(recall.path("status").asText().equals("STOPPED")
            && recall.path("mayResumeScanning").isBoolean() && !recall.path("mayResumeScanning").asBoolean()
            && recall.path("stockReleased").isBoolean() && !recall.path("stockReleased").asBoolean()
            && recall.path("changeRequestId").asText().equals(command.recallId().toString())
            && recall.path("orderId").asText().equals(origin.order().toString())
            && recall.path("assignmentId").asText().equals(origin.assignment().toString())
            && recall.path("issuedRecoveryEpoch").asText().equals(command.epoch()));
        command.acknowledgement().fields().forEachRemaining(e -> corruptUnless(e.getValue().equals(recall.path(e.getKey()))));
    }

    private void verifyStop(JsonNode stop, HandoverStopCommand command, HandoverStopStore.Origin origin) {
        corruptUnless((command.temporary() == null) == (origin.temporary() == null));
        if (command.temporary() == null) verifyRecall(stop, command, origin);
        else corruptUnless(stop.equals(HandoverTemporaryStopEvidence.descriptor(command, origin)));
    }

    private void row(Map<String, Object> row, String kind, UUID id, HandoverStopCommand command,
            HandoverStopStore.Origin origin, String digest, JsonNode receipt) {
        corruptUnless(kind.equals(row.get("kind")) && Arrays.equals(Ids.bytes(id), (byte[]) row.get("id"))
            && Arrays.equals(Ids.bytes(command.caseId()), (byte[]) row.get("case_id"))
            && Arrays.equals(Ids.bytes(origin.device()), (byte[]) row.get("device_id"))
            && digest.equals(HexFormat.of().formatHex((byte[]) row.get("payload_sha256")))
            && row.get("high_watermark") instanceof Number high && high.longValue() == command.inputHigh()
            && recorded(row.get("recorded_at")).equals(Instant.parse(receipt.path("recordedAt").asText())));
    }

    private Instant recorded(Object value) {
        if (value instanceof Timestamp stamp) return stamp.toInstant();
        if (value instanceof LocalDateTime time) return time.toInstant(ZoneOffset.UTC);
        throw HandoverCommand.corrupt();
    }

    private JsonNode source(HandoverStopStore.Origin origin) {
        var result = SubmissionJson.object().put("warehouseId", origin.warehouse().toString()).put("scopeId", origin.scope().toString())
            .put("orderId", origin.order().toString()).put("submissionId", origin.submission().toString())
            .put("assignmentId", origin.assignment() == null ? null : origin.assignment().toString()).put("deviceId", origin.device().toString())
            .put("operatorId", origin.operator().toString()).put("sourceRecoveryEpoch", origin.epoch())
            .put("workRound", Long.toString(origin.round()));
        if (origin.temporary() != null) result.put("sourceKind", "TEMP")
            .put("authorizationId", origin.temporary().authorization().toString())
            .put("creatorDeviceId", origin.temporary().creator().toString()).put("clientOrderId", origin.temporary().client().toString());
        return result;
    }
}
