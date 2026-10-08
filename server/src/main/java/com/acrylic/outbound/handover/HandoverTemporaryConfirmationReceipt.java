package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.PdaSessions;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.handover.HandoverCommand.corruptUnless;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
final class HandoverTemporaryConfirmationReceipt {
    private final HandoverStopSignature signatures;
    HandoverTemporaryConfirmationReceipt(HandoverStopSignature signatures) { this.signatures = signatures; }

    HandoverTemporaryConfirmationStore.Receipt create(HandoverTemporaryConfirmationCommand command, JsonNode manifest,
            PdaSessions.Principal actor) {
        var id = Ids.next(); var at = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var body = object().put("receiptVersion", 1).put("receiptId", id.toString()).put("caseId", command.caseId().toString())
            .put("status", "TARGET_CONFIRMED").put("recordedAt", at.toString()).put("commandSha256", command.digest());
        body.set("originalCommand", command.bound());
        body.set("preparedTicket", manifest.path("preparedTicket").deepCopy());
        body.set("checklist", manifest.path("checklist").deepCopy());
        body.set("capabilities", HandoverReceiptCodec.capabilities());
        body.putObject("actor").put("userId", actor.userId().toString()).put("deviceId", actor.deviceId().toString())
            .put("warehouseId", actor.warehouseId().toString()).put("userAuthVersion", actor.userAuthVersion())
            .put("deviceVersion", actor.deviceVersion()).put("sessionId", actor.sessionId().toString());
        return new HandoverTemporaryConfirmationStore.Receipt(id, at, canonical(signatures.signTemporaryConfirmation(body)).getBytes(StandardCharsets.UTF_8));
    }

    byte[] replay(Map<String, Object> row, HandoverTemporaryConfirmationCommand command, PdaSessions.Principal actor) {
        if (!actor.userId().toString().equals(row.get("actor_id")) || !actor.deviceId().toString().equals(row.get("device_id")))
            throw HandoverCommand.missing();
        byte[] response;
        try { response = verified(row, actor); }
        catch (RuntimeException invalid) { throw HandoverCommand.corrupt(); }
        corruptUnless(command.caseId().toString().equals(row.get("case_id")));
        if (!Arrays.equals((byte[]) row.get("command_bytes"), command.bytes())) throw HandoverCommand.conflict();
        return response.clone();
    }

    JsonNode verifyForActivation(Map<String, Object> row, PdaSessions.Principal actor) {
        corruptUnless(actor.sessionId().toString().equals(row.get("session_id"))
            && actor.userId().toString().equals(row.get("actor_id")) && actor.deviceId().toString().equals(row.get("device_id")));
        JsonNode receipt;
        try { receipt = parse(verified(row, actor)); }
        catch (RuntimeException invalid) { throw HandoverCommand.corrupt(); }
        corruptUnless(receipt.path("actor").path("userAuthVersion").asText().equals(actor.userAuthVersion())
            && receipt.path("actor").path("deviceVersion").asText().equals(actor.deviceVersion()));
        return receipt;
    }

    private byte[] verified(Map<String, Object> row, PdaSessions.Principal actor) {
        byte[] response = (byte[]) row.get("receipt_bytes"); var receipt = parse(response);
        signatures.verifyTemporaryConfirmation(receipt);
        corruptUnless(HandoverCommand.sha(response).equals(row.get("receipt_sha256")) &&
            HandoverCommand.sha((byte[]) row.get("command_bytes")).equals(row.get("command_sha256")));
        corruptUnless(receipt.path("originalCommand").equals(parse((byte[]) row.get("command_bytes")))
            && receipt.path("commandSha256").asText().equals(row.get("command_sha256")));
        corruptUnless(receipt.path("caseId").asText().equals(row.get("case_id"))
            && receipt.path("originalCommand").path("caseId").asText().equals(row.get("case_id"))
            && receipt.path("receiptId").asText().equals(row.get("id"))
            && receipt.path("originalCommand").path("command").path("commandId").asText().equals(row.get("command_id"))
            && receipt.path("originalCommand").path("command").path("targetAuthorizationId").asText().equals(row.get("authorization_id")));
        verifyActor(receipt, row, actor);
        var original = receipt.path("originalCommand").path("command");
        var qualification = receipt.path("preparedTicket").path("temporaryQualification");
        corruptUnless(sha(receipt.path("preparedTicket")).equals(original.path("preparedTicketSha256").asText())
            && sha(receipt.path("checklist")).equals(original.path("checklistSha256").asText())
            && qualification.path("payloadSha256").equals(original.path("payloadSha256"))
            && qualification.path("authorizationId").equals(original.path("targetAuthorizationId"))
            && original.path("physicalConfirmed").isBoolean() && original.path("physicalConfirmed").booleanValue());
        return response;
    }

    private void verifyActor(JsonNode receipt, Map<String, Object> row, PdaSessions.Principal actor) {
        var signedActor = receipt.path("actor");
        corruptUnless(signedActor.path("userId").asText().equals(row.get("actor_id"))
            && signedActor.path("deviceId").asText().equals(row.get("device_id"))
            && (row.get("session_id") == null ? !signedActor.has("sessionId")
                : signedActor.path("sessionId").asText().equals(row.get("session_id")))
            && signedActor.path("warehouseId").asText().equals(actor.warehouseId().toString())
            && receipt.path("receiptVersion").asInt() == 1
            && Instant.parse(receipt.path("recordedAt").asText()).equals(recordedAt(row.get("recorded_at")))
            && "TARGET_CONFIRMED".equals(receipt.path("status").asText())
            && receipt.path("capabilities").equals(HandoverReceiptCodec.capabilities()));
    }

    private Instant recordedAt(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.LocalDateTime local) return local.toInstant(java.time.ZoneOffset.UTC);
        throw HandoverCommand.corrupt();
    }
}
