package com.acrylic.outbound.handover;

import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.handover.HandoverCommand.corruptUnless;

/** Verifies both receipt domains before a stored recall proof can become handover evidence. */
@Component
final class HandoverStopSignature {
    private static final String ISSUER = "acrylic-outbound";
    private static final String AUDIENCE = "acrylic-pda-handover-stop";
    private final OfflineSigner signer;
    HandoverStopSignature(OfflineSigner signer) { this.signer = signer; }

    ObjectNode sign(ObjectNode receipt) {
        return sign(receipt, AUDIENCE);
    }

    ObjectNode signTemporaryConfirmation(ObjectNode receipt) {
        return sign(receipt, "acrylic-pda-temporary-handover-confirmation");
    }

    private ObjectNode sign(ObjectNode receipt, String audience) {
        return receipt.deepCopy().put("signedReceipt", signer.sign(Map.of(
            "iss", ISSUER, "aud", audience, "receipt", receipt)).token());
    }

    void verify(JsonNode receipt) { verify(receipt, AUDIENCE); }
    void verifyRecall(JsonNode receipt) { verify(receipt, "acrylic-pda-recall"); }
    void verifyTemporaryConfirmation(JsonNode receipt) { verify(receipt, "acrylic-pda-temporary-handover-confirmation"); }

    private void verify(JsonNode receipt, String audience) {
        try {
            corruptUnless(receipt.isObject() && receipt.path("signedReceipt").isTextual());
            String[] parts = receipt.path("signedReceipt").asText().split("\\.", -1);
            corruptUnless(parts.length == 3);
            var header = SubmissionJson.parse(decode(parts[0]));
            corruptUnless(header.size() == 3 && header.path("kid").isTextual()
                && "RS256".equals(header.path("alg").asText()) && "JWT".equals(header.path("typ").asText()));
            var key = signer.publicKeyFor(header.path("kid").asText());
            corruptUnless(key != null && key.keyId().equals(header.path("kid").asText()) && key.algorithm().equals("RS256"));
            var rsa = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey())));
            var verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(rsa);
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            corruptUnless(verifier.verify(decode(parts[2])));
            var unsigned = (ObjectNode) receipt.deepCopy(); unsigned.remove("signedReceipt");
            var claims = SubmissionJson.parse(decode(parts[1]));
            corruptUnless(claims.size() == 3 && ISSUER.equals(claims.path("iss").asText())
                && audience.equals(claims.path("aud").asText()) && unsigned.equals(claims.path("receipt")));
        } catch (GeneralSecurityException | RuntimeException invalid) { throw HandoverCommand.corrupt(); }
    }

    private byte[] decode(String value) {
        corruptUnless(value.matches("[A-Za-z0-9_-]+"));
        byte[] result = Base64.getUrlDecoder().decode(value);
        corruptUnless(Base64.getUrlEncoder().withoutPadding().encodeToString(result).equals(value));
        return result;
    }
}
