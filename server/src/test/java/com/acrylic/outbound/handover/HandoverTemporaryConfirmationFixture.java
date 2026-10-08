package com.acrylic.outbound.handover;

import com.acrylic.outbound.pda.PdaSessions;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Minimal confirmation evidence with generated RSA keys, never an original device identity. */
final class HandoverTemporaryConfirmationFixture {
    final UUID kase = UUID.randomUUID(), authorization = UUID.randomUUID(), key = UUID.randomUUID();
    final PdaSessions.Principal actor = new PdaSessions.Principal(UUID.randomUUID(), UUID.randomUUID(), "recipient",
        UUID.randomUUID(), UUID.randomUUID(), "1", "2");
    final ObjectNode manifest = object(), command;
    final HandoverStopSignature signatures;
    final HandoverTemporaryConfirmationReceipt receipts;

    HandoverTemporaryConfirmationFixture(Path directory) throws Exception {
        signatures = new HandoverStopSignature(HandoverTemporaryCredentialFixture.signer(directory, "confirmation", ""));
        receipts = new HandoverTemporaryConfirmationReceipt(signatures);
        var ticket = object().put("caseId", kase.toString());
        ticket.putObject("temporaryQualification").put("authorizationId", authorization.toString()).put("payloadSha256", "a".repeat(64));
        manifest.set("preparedTicket", ticket); manifest.putArray("checklist");
        command = object().put("commandVersion", 1).put("commandId", key.toString()).put("expectedCaseVersion", "4")
            .put("targetAuthorizationId", authorization.toString()).put("preparedTicketSha256", sha(ticket))
            .put("payloadSha256", "a".repeat(64)).put("checklistSha256", sha(manifest.path("checklist"))).put("physicalConfirmed", true);
    }

    HandoverTemporaryConfirmationCommand parsed(ObjectNode body) {
        return HandoverTemporaryConfirmationCommand.parse(kase, "1", canonical(body).getBytes(StandardCharsets.UTF_8));
    }

    Map<String, Object> stored() {
        var parsed = parsed(command); var receipt = receipts.create(parsed, manifest, actor);
        var row = new HashMap<String, Object>();
        row.put("id", receipt.id().toString()); row.put("case_id", kase.toString()); row.put("command_id", key.toString());
        row.put("authorization_id", authorization.toString()); row.put("actor_id", actor.userId().toString());
        row.put("device_id", actor.deviceId().toString()); row.put("command_bytes", parsed.bytes());
        row.put("session_id", actor.sessionId().toString());
        row.put("command_sha256", parsed.digest()); row.put("receipt_bytes", receipt.bytes());
        row.put("receipt_sha256", HandoverCommand.sha(receipt.bytes())); row.put("recorded_at", Timestamp.from(receipt.recordedAt()));
        return row;
    }
}
