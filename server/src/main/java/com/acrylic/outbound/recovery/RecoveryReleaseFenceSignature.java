package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Read-only handover-purpose signatures from independently supplied public keys. */
final class RecoveryReleaseFenceSignature {
    static JsonNode verify(byte[] original,RecoveryReceiptKeys keys) {
        require(original.length>0 && original.length<=262144,"RECOVERY_SCOPE_RECEIPT_SIZE");
        try {
            var receipt=SubmissionJson.parse(original);
            require(Arrays.equals(original,SubmissionJson.canonical(receipt).getBytes(StandardCharsets.UTF_8)),"RECOVERY_SCOPE_RECEIPT_CANONICAL");
            String[] parts=text(receipt,"signedReceipt").split("\\.",-1); require(parts.length==3,"RECOVERY_SCOPE_RECEIPT_TOKEN");
            var header=SubmissionJson.parse(decode(parts[0])); fields(header,"alg typ kid");
            require(text(header,"alg").equals("RS256") && text(header,"typ").equals("JWT"),"RECOVERY_SCOPE_RECEIPT_ALGORITHM");
            var verifier=Signature.getInstance("SHA256withRSA"); verifier.initVerify(keys.requireKey(text(header,"kid")));
            verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
            require(verifier.verify(decode(parts[2])),"RECOVERY_SCOPE_RECEIPT_SIGNATURE");
            var claims=SubmissionJson.parse(decode(parts[1])); fields(claims,"iss aud receipt");
            require(text(claims,"iss").equals("acrylic-outbound-server") && text(claims,"aud").equals("acrylic-outbound-handover-receipt"),
                "RECOVERY_SCOPE_RECEIPT_PURPOSE");
            var unsigned=(com.fasterxml.jackson.databind.node.ObjectNode)receipt.deepCopy(); unsigned.remove("signedReceipt");
            require(unsigned.equals(claims.path("receipt")),"RECOVERY_SCOPE_RECEIPT_CONTENT"); return unsigned;
        } catch(RecoveryControlException failure) { throw failure; }
        catch(GeneralSecurityException | RuntimeException invalid) { throw new RecoveryControlException("RECOVERY_SCOPE_RECEIPT_INVALID",invalid); }
    }
    private static byte[] decode(String value) {
        require(value.matches("[A-Za-z0-9_-]+"),"RECOVERY_SCOPE_RECEIPT_TOKEN"); var bytes=Base64.getUrlDecoder().decode(value);
        require(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(value),"RECOVERY_SCOPE_RECEIPT_TOKEN"); return bytes;
    }
    private RecoveryReleaseFenceSignature() { }
}
