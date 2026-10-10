package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.security.*;
import java.util.Base64;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Operations-only signature over strict canonical approval bytes, separate from server receipts. */
final class RecoveryOperationsSignature {
    static JsonNode verify(byte[] bytes,RecoveryOperationsAuthority authority) {
        var envelope=readCanonical(bytes);
        fields(envelope,"formatVersion algorithm keySha256 approval signature"); version(envelope);
        require(text(envelope,"algorithm").equals("RS256"),"RECOVERY_OPERATIONS_ALGORITHM");
        require(text(envelope,"keySha256").equals(sha256(authority.publicKey().getEncoded())),"RECOVERY_OPERATIONS_KEY_MISMATCH");
        String signature=text(envelope,"signature");
        require(signature.matches("[A-Za-z0-9_-]+"),"RECOVERY_OPERATIONS_SIGNATURE_ENCODING");
        try {
            byte[] raw=Base64.getUrlDecoder().decode(signature);
            require(Base64.getUrlEncoder().withoutPadding().encodeToString(raw).equals(signature),"RECOVERY_OPERATIONS_SIGNATURE_ENCODING");
            var verifier=Signature.getInstance("SHA256withRSA"); verifier.initVerify(authority.publicKey());
            verifier.update(RecoveryControlJson.encode(envelope.path("approval")));
            require(verifier.verify(raw),"RECOVERY_OPERATIONS_SIGNATURE_INVALID"); return envelope.path("approval").deepCopy();
        } catch(GeneralSecurityException | IllegalArgumentException error) { throw new RecoveryControlException("RECOVERY_OPERATIONS_SIGNATURE_INVALID",error); }
    }
    private RecoveryOperationsSignature() { }
}
