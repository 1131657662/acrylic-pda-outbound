package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Signed handover receipt. It mirrors the R06 command receipt discipline (single audit event per receipt, original
 * bytes replayed by key) without touching that chain, and it never advertises a capability: all five flags stay
 * false, so an approved or activated case still cannot release stock, resume scanning or allow local deletion.
 */
@Component
public class HandoverReceiptCodec {
    private static final String ISSUER = "acrylic-outbound-server";
    private static final String AUDIENCE = "acrylic-outbound-handover-receipt";

    private final OfflineSigner signer;
    public HandoverReceiptCodec(OfflineSigner signer) { this.signer=signer; }

    public Result sign(HandoverCommand.Write write,JsonNode decision) {
        var unsigned=SubmissionJson.object().put("receiptVersion",1)
            .put("receiptId",write.receiptId().toString()).put("commandId",write.command().key())
            .put("routeScope",HandoverCommand.ROUTE_SCOPE).put("commandType",write.command().kind())
            .put("commandSha256",write.command().digest()).put("status","RECORDED")
            .put("recordedAt",write.recordedAt().toString()).put("eventId",write.eventId().toString());
        unsigned.set("caseContext",write.caseFacts());
        unsigned.set("actor",write.actor());
        unsigned.set("businessDecision",decision==null?com.fasterxml.jackson.databind.node.NullNode.getInstance():decision);
        unsigned.set("expected",write.command().expected());
        unsigned.set("capabilities",capabilities());
        validate(unsigned);
        byte[] unsignedBytes=bytes(unsigned);
        var signed=signer.sign(Map.of("iss",ISSUER,"aud",AUDIENCE,"receipt",unsigned));
        var response=unsigned.deepCopy().put("signedReceipt",signed.token());
        byte[] original=bytes(response);
        return new Result(original,HandoverCommand.sha(original),signed.keyId(),signed.token());
    }

    static JsonNode capabilities() {
        return SubmissionJson.object().put("writeConfirmationAvailable",false).put("releaseAllowed",false)
            .put("mayResumeScanning",false).put("stockReleased",false).put("localDeletionAllowed",false);
    }

    private static void validate(JsonNode receipt) {
        var required=java.util.Set.of("receiptVersion","receiptId","commandId","routeScope","commandType","commandSha256",
            "status","recordedAt","eventId","caseContext","actor","businessDecision","expected","capabilities");
        var actual=new java.util.HashSet<String>();receipt.fieldNames().forEachRemaining(actual::add);
        if(!actual.equals(required)) throw new BusinessException(409,"HANDOVER_RECEIPT_SHAPE","回执字段不符合交接契约");
        if(!receipt.path("capabilities").equals(capabilities())) throw new BusinessException(409,"HANDOVER_RECEIPT_SHAPE","回执能力位被篡改");
        if(!HandoverCommand.KINDS_SET.contains(receipt.path("commandType").asText())) throw new BusinessException(409,"HANDOVER_RECEIPT_SHAPE","回执命令类型无效");
    }

    static byte[] bytes(JsonNode node) {
        return SubmissionJson.canonical(node).getBytes(StandardCharsets.UTF_8);
    }

    /** original = full response bytes (what a retry must replay); sha256 is lowercase hex. */
    public record Result(byte[] original,String originalSha256,String keyId,String token) {
        public Result { original=original.clone(); }
        @Override public byte[] original() { return original.clone(); }
    }
}
