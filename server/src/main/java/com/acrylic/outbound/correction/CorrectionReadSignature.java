package com.acrylic.outbound.correction;

import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Verifies the original correction signature with configured current or historical public keys. */
final class CorrectionReadSignature {
    private CorrectionReadSignature() { }

    static void verify(String token, JsonNode unsigned, OfflineSigner signer) {
        String[] parts = token.split("\\.", -1);
        require(parts.length == 3);
        var header = SubmissionJson.parse(decode(parts[0]));
        require(header.size() == 3 && "RS256".equals(header.path("alg").asText())
            && "JWT".equals(header.path("typ").asText()));
        var key = signer.publicKeyFor(header.path("kid").asText());
        require(key != null && key.keyId().equals(header.path("kid").asText()) && "RS256".equals(key.algorithm()));
        verifyRsa(parts, key.publicKey());
        var claims = SubmissionJson.parse(decode(parts[1]));
        require(claims.size() == 3 && "acrylic-outbound-server".equals(claims.path("iss").asText())
            && "acrylic-outbound-correction-receipt".equals(claims.path("aud").asText())
            && unsigned.equals(claims.get("receipt")));
    }

    private static void verifyRsa(String[] parts, String encodedKey) {
        try {
            var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encodedKey)));
            var verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(key);
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            require(verifier.verify(decode(parts[2])));
        } catch (GeneralSecurityException failure) { throw CorrectionCommand.corrupt(); }
    }

    private static byte[] decode(String part) {
        require(part.matches("[A-Za-z0-9_-]+"));
        byte[] decoded = Base64.getUrlDecoder().decode(part);
        require(Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(part));
        return decoded;
    }

    static void require(boolean valid) { if (!valid) throw CorrectionCommand.corrupt(); }
}
