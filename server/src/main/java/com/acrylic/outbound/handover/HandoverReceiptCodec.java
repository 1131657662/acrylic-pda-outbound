package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

    /**
     * Verifies a stored receipt before it is replayed as the answer to a retry. A replay must never hand back bytes
     * that cannot be re-proved: the stored response has to parse, it has to be the same object the row's
     * {@code response_sha256} / {@code command_id} / {@code case_id} describe, and its signature has to verify against
     * the key the row recorded. Anything else is an integrity failure, not a retry.
     *
     * <p>The row's own {@code response_bytes} are canonicalised before hashing, so a row whose hash matches only in a
     * non-canonical encoding is refused as well.
     */
    public JsonNode verify(Map<String,Object> row) {
        try {
            byte[] stored = (byte[]) row.get("response_bytes");
            if (stored == null || stored.length == 0) throw HandoverCommand.corrupt();
            byte[] expected = (byte[]) row.get("response_sha256");
            if (expected == null) throw HandoverCommand.corrupt();
            var receipt = SubmissionJson.parse(new String(stored,StandardCharsets.UTF_8));
            HandoverCommand.corruptUnless(receipt.isObject());
            HandoverCommand.corruptUnless(HandoverCommand.sha(bytes(receipt)).equals(hex(expected)));
            var unsigned = receipt.deepCopy();
            HandoverCommand.corruptUnless(unsigned.hasNonNull("signedReceipt") && unsigned.isObject());
            ((com.fasterxml.jackson.databind.node.ObjectNode) unsigned).remove("signedReceipt");
            validate(unsigned);
            HandoverCommand.corruptUnless(text(unsigned,"receiptId").equals(rowText(row,"id"))
                && text(unsigned,"commandId").equals(rowText(row,"original_key"))
                && text(unsigned,"commandType").equals(rowText(row,"command_type"))
                && text(unsigned,"commandSha256").equals(rowText(row,"command_sha256")));
            verifySignature(text(receipt,"signedReceipt"),unsigned);
            return unsigned;
        } catch (BusinessException alreadyNamed) {
            if ("HANDOVER_RECEIPT_INTEGRITY_FAILURE".equals(alreadyNamed.code())
                || "HANDOVER_RECEIPT_SHAPE".equals(alreadyNamed.code())) throw HandoverCommand.corrupt();
            throw alreadyNamed;
        } catch (RuntimeException damaged) {
            throw HandoverCommand.corrupt();
        }
    }

    /** The plain RSA/JWS check, mirroring the R06 command-signature discipline without depending on it. */
    private void verifySignature(String token,JsonNode unsigned) {
        // The signing key id lives in the token header, which is the only place that records it: the receipt table has
        // no signature_key_id column, so reading the header is the authentic lookup rather than a guess.
        String[] parts = token.split("\\.",-1);
        HandoverCommand.corruptUnless(parts.length==3);
        var header = SubmissionJson.parse(new String(decode(parts[0]),StandardCharsets.UTF_8));
        var keyId = header.path("kid").asText(null);
        HandoverCommand.corruptUnless(keyId!=null && header.size()==3 && "RS256".equals(header.path("alg").asText())
            && "JWT".equals(header.path("typ").asText()));
        var key = signer.publicKeyFor(keyId);
        HandoverCommand.corruptUnless(keyId.equals(key.keyId()) && "RS256".equals(key.algorithm()));
        try {
            var rsa = java.security.KeyFactory.getInstance("RSA")
                .generatePublic(new java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey())));
            var verifier = java.security.Signature.getInstance("SHA256withRSA");
            verifier.initVerify(rsa);
            verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
            HandoverCommand.corruptUnless(verifier.verify(decode(parts[2])));
        } catch (java.security.GeneralSecurityException failure) {
            throw HandoverCommand.corrupt();
        }
        var claims = SubmissionJson.parse(new String(decode(parts[1]),StandardCharsets.UTF_8));
        HandoverCommand.corruptUnless(claims.size()==3 && ISSUER.equals(claims.path("iss").asText())
            && AUDIENCE.equals(claims.path("aud").asText()) && unsigned.equals(claims.get("receipt")));
    }

    private static byte[] decode(String part) {
        HandoverCommand.corruptUnless(part.matches("[A-Za-z0-9_-]+"));
        byte[] bytes = Base64.getUrlDecoder().decode(part);
        HandoverCommand.corruptUnless(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(part));
        return bytes;
    }

    private static String hex(byte[] value) {
        return java.util.HexFormat.of().formatHex(value);
    }

    private static String text(JsonNode node,String field) {
        var value = node.get(field);
        return value==null||value.isNull()?null:value.asText();
    }

    private static String text(Map<String,Object> row,String field) {
        var value = row.get(field);
        return value==null?null:value.toString();
    }

    /**
     * The row value in the form the receipt spells it: binary identifiers are matched by their UUID text, binary
     * digests by lowercase hex. Without the conversion a BINARY(16) column would be compared byte-for-byte against a
     * UUID string and every replay would look corrupt.
     */
    private static String rowText(Map<String,Object> row,String field) {
        var value = row.get(field);
        if (value == null) return null;
        if (!(value instanceof byte[] bytes)) return value.toString();
        if (bytes.length == 16) {
            var buffer = java.nio.ByteBuffer.wrap(bytes);
            return new java.util.UUID(buffer.getLong(),buffer.getLong()).toString();
        }
        if (bytes.length == 32) return hex(bytes);
        return hex(bytes);
    }

    /** original = full response bytes (what a retry must replay); sha256 is lowercase hex. */
    public record Result(byte[] original,String originalSha256,String keyId,String token) {
        public Result { original=original.clone(); }
        @Override public byte[] original() { return original.clone(); }
    }
}
