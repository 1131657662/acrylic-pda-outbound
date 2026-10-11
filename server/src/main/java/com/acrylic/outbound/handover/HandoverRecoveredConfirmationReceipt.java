package com.acrylic.outbound.handover;

import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.recovery.RecoveryRecipientSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Separate signature purpose cannot be consumed as an ordinary confirmation or an activation ticket. */
@Component
final class HandoverRecoveredConfirmationReceipt {
    private final HandoverStopSignature signatures;
    HandoverRecoveredConfirmationReceipt(HandoverStopSignature signatures) { this.signatures=signatures; }
    byte[] create(Context context,UUID id,Instant at,String requestId) {
        return HandoverReceiptCodec.bytes(signatures.signRecoveredConfirmation(body(context,id,at,requestId)));
    }
    void verify(byte[] bytes,Context context,UUID id,Instant at,String requestId) {
        var receipt=parse(bytes); signatures.verifyRecoveredConfirmation(receipt);
        var unsigned=(ObjectNode)receipt.deepCopy(); unsigned.remove("signedReceipt");
        HandoverCommand.corruptUnless(java.util.Arrays.equals(bytes,HandoverReceiptCodec.bytes(receipt))
            && unsigned.equals(body(context,id,at,requestId)));
    }
    private ObjectNode body(Context context,UUID id,Instant at,String requestId) {
        var manifest=parse(context.document().manifest()); var actor=context.actor(); var session=context.session();
        var body=object().put("formatVersion",1).put("purpose","RECOVERED_TEMP_HANDOVER_CONFIRMATION")
            .put("receiptId",id.toString()).put("recordedAt",at.toString()).put("requestId",requestId)
            .put("status","RECOVERED_TARGET_CONFIRMED").put("commandSha256",context.command().sha256())
            .put("recipientSessionSha256",session.sha256());
        body.set("originalCommand",context.command().body()); body.set("qualification",session.qualification().preparationDescriptor());
        body.set("preparedTicket",manifest.path("preparedTicket")); body.set("checklist",manifest.path("checklist"));
        body.set("capabilities",HandoverReceiptCodec.capabilities());
        body.putObject("actor").put("userId",actor.userId().toString()).put("deviceId",actor.deviceId().toString())
            .put("warehouseId",actor.warehouseId().toString()).put("userAuthVersion",actor.userAuthVersion())
            .put("deviceVersion",actor.deviceVersion()).put("sessionId",actor.sessionId().toString());
        return body;
    }
    record Context(HandoverRecoveredConfirmationCommand command,RecoveryRecipientSession session,
            HandoverRecoveredTemporaryDownload.Result document,PdaSessions.Principal actor) { }
}
