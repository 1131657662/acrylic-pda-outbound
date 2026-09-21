package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.TemporaryProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CommandReceiptCodec {
    private final OfflineSigner signer;
    public CommandReceiptCodec(OfflineSigner signer) { this.signer=signer; }
    public Receipt sign(CommandWrite write) {
        var input=write.command();var unsigned=SubmissionJson.object().put("receiptVersion",1)
            .put("receiptId",write.receiptId().toString()).put("commandId",input.key()).put("routeScope",TemporaryProtocol.ROUTE_SCOPE)
            .put("commandType",input.kind()).put("commandSha256",input.digest()).put("status","INTERNAL_TEST_ONLY")
            .putNull("businessDecision").put("recordedAt",write.recordedAt().toString()).put("eventId",write.eventId().toString())
            .put("resultingDecisionVersion",Long.toString(Math.addExact(write.context().version("decisionVersion"),1)));
        unsigned.set("originalContext",write.context().json());unsigned.set("actor",write.actor());
        unsigned.set("expected",input.expected());unsigned.set("capabilities",capabilities());
        CommandReceiptShape.validate(unsigned);
        byte[] unsignedBytes=bytes(unsigned);
        var signed=signer.sign(Map.of("iss",TemporaryProtocol.ISSUER,"aud",TemporaryProtocol.RECEIPT_AUDIENCE,"receipt",unsigned));
        var response=unsigned.deepCopy().put("signedReceipt",signed.token());byte[] original=bytes(response);
        return new Receipt(original,TemporaryProtocol.originalResponseSha256(original),unsignedBytes,
            TemporaryProtocol.unsignedReceiptSha256(unsignedBytes),signed.keyId(),signed.token());
    }
    public static JsonNode capabilities() {
        return SubmissionJson.object().put("writeConfirmationAvailable",false).put("releaseAllowed",false)
            .put("mayResumeScanning",false).put("stockReleased",false).put("localDeletionAllowed",false);
    }
    static byte[] bytes(JsonNode node) {
        return TemporaryProtocol.canonical(SubmissionJson.canonical(node).getBytes(StandardCharsets.UTF_8));
    }
    public record Receipt(byte[] original,String originalSha256,byte[] unsigned,String unsignedSha256,String keyId,String token) {
        public Receipt { original=original.clone();unsigned=unsigned.clone(); }
        @Override public byte[] original() { return original.clone(); }
        @Override public byte[] unsigned() { return unsigned.clone(); }
    }
}
