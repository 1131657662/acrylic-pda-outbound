package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Verifies an original server receipt with public keys only; does not recreate any business facts. */
public final class RecoveryReceiptVerifier {
    private final RecoveryReceiptKeys keys;

    public RecoveryReceiptVerifier(RecoveryReceiptKeys keys) { this.keys = Objects.requireNonNull(keys); }

    public JsonNode verify(byte[] originalReceipt) {
        require(originalReceipt != null && originalReceipt.length > 0 && originalReceipt.length <= 1048576,
            "RECOVERY_RECEIPT_SIZE");
        try { return checked(SubmissionJson.parse(originalReceipt.clone())); }
        catch (RecoveryControlException refusal) { throw refusal; }
        catch (GeneralSecurityException | RuntimeException invalid) {
            throw new RecoveryControlException("RECOVERY_RECEIPT_INVALID", invalid);
        }
    }

    private JsonNode checked(JsonNode receipt) throws GeneralSecurityException {
        String token = text(receipt, "signedReceipt");
        require(token.length() <= 1048576, "RECOVERY_RECEIPT_SIZE");
        String[] parts = token.split("\\.", -1);
        require(parts.length == 3, "RECOVERY_RECEIPT_TOKEN");
        var header = SubmissionJson.parse(decode(parts[0]));
        fields(header, "alg typ kid");
        require("RS256".equals(text(header, "alg")) && "JWT".equals(text(header, "typ")), "RECOVERY_RECEIPT_ALGORITHM");
        var verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keys.requireKey(text(header, "kid")));
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        require(verifier.verify(decode(parts[2])), "RECOVERY_RECEIPT_SIGNATURE");
        var claims = SubmissionJson.parse(decode(parts[1]));
        fields(claims, "iss aud receipt");
        require("acrylic-outbound".equals(text(claims, "iss")) && "acrylic-pda-receipt".equals(text(claims, "aud")),
            "RECOVERY_RECEIPT_PURPOSE");
        ObjectNode unsigned = receipt.deepCopy(); unsigned.remove("signedReceipt");
        require(unsigned.equals(claims.path("receipt")), "RECOVERY_RECEIPT_CONTENT");
        return receipt.deepCopy();
    }

    private byte[] decode(String value) {
        require(value.matches("[A-Za-z0-9_-]+"), "RECOVERY_RECEIPT_TOKEN");
        byte[] bytes = Base64.getUrlDecoder().decode(value);
        require(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(value), "RECOVERY_RECEIPT_TOKEN");
        return bytes;
    }
}
