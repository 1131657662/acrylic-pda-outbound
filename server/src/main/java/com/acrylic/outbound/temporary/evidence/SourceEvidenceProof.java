package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class SourceEvidenceProof {
    private final OfflineSigner signer;
    public SourceEvidenceProof(OfflineSigner signer) { this.signer=signer; }
    JsonNode read(SubmissionStore.Head head,SourceEvidenceQueries.FileRecord payload) {
        if(head.receipt()==null)return object().putNull("originalStoredReceiptUtf8").putNull("originalStoredReceiptSha256")
            .putNull("signedReceipt").put("verification","NOT_PRESENT");
        var raw=head.receipt();var result=object().put("originalStoredReceiptUtf8",raw)
            .put("originalStoredReceiptSha256",ImportFiles.sha256(raw.getBytes(StandardCharsets.UTF_8))).putNull("signedReceipt");
        JsonNode receipt;
        try { receipt=parse(raw);if(!receipt.isObject())return result.put("verification","INVALID_FORMAT"); }
        catch(RuntimeException malformed) { return result.put("verification","INVALID_FORMAT"); }
        result.put("signedReceipt",receipt.path("signedReceipt").isTextual()?receipt.path("signedReceipt").asText():null);
        String verification;
        try { verification=verify(receipt,signer::publicKeyFor); }
        catch(com.acrylic.outbound.common.BusinessException unavailable) { verification="UNVERIFIABLE_KEY"; }
        if(!matches(receipt,head,payload))verification="CONTROL_MISMATCH";
        return result.put("verification",verification);
    }
    static String verify(JsonNode receipt,OfflineSigner.PublicKeyView key) {
        return verify(receipt,kid -> key.keyId().equals(kid) ? key : null);
    }
    static String verify(JsonNode receipt,java.util.function.Function<String,OfflineSigner.PublicKeyView> keys) {
        try {
            String[] parts=receipt.path("signedReceipt").asText().split("\\.",-1);
            if(parts.length!=3)return "INVALID_SIGNATURE";
            var decoder=Base64.getUrlDecoder();var header=parse(decoder.decode(parts[0]));
            if(!header.path("alg").asText().equals("RS256") || !header.path("typ").asText().equals("JWT"))return "INVALID_SIGNATURE";
            var key=keys.apply(header.path("kid").asText());
            if(key==null)return "UNVERIFIABLE_KEY";
            if(!key.keyId().equals(header.path("kid").asText()) || !key.algorithm().equals("RS256"))return "INVALID_SIGNATURE";
            var publicKey=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey())));
            var verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(publicKey);
            verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
            if(!verifier.verify(decoder.decode(parts[2])))return "INVALID_SIGNATURE";
            var claims=parse(decoder.decode(parts[1]));var original=receipt.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)original).remove("signedReceipt");
            return claims.path("iss").asText().equals("acrylic-outbound") && claims.path("aud").asText().equals("acrylic-pda-receipt")
                && claims.path("receipt").equals(original)?"VERIFIED":"INVALID_SIGNATURE";
        } catch(BusinessException unavailable) { throw unavailable; }
        catch(GeneralSecurityException | RuntimeException error) { return "INVALID_SIGNATURE"; }
    }
    private static boolean matches(JsonNode receipt,SubmissionStore.Head h,SourceEvidenceQueries.FileRecord payload) {
        var c=h.control();var expected=object().put("submissionId",c.id().toString()).put("status",h.status())
            .put("orderId",h.order()==null?null:h.order().toString()).put("freezeDeviceId",c.device().toString())
            .put("workRound",Long.toString(c.round())).put("issuedRecoveryEpoch",c.epoch());expected.set("source",c.source());
        expected.put("payloadSha256",payload==null?null:payload.sha256());
        for(var fields=expected.fields();fields.hasNext();) { var e=fields.next();if(!e.getValue().equals(receipt.get(e.getKey())))return false; }
        return !h.status().equals("ABORTED") || receipt.path("payloadPresent").isBoolean() && receipt.path("payloadPresent").asBoolean()==(payload!=null)
            && receipt.path("stockReleased").isBoolean() && !receipt.path("stockReleased").asBoolean()
            && receipt.path("mayResumeScanning").isBoolean() && !receipt.path("mayResumeScanning").asBoolean();
    }
}
