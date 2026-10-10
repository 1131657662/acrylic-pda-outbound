package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Native values derived only after the independently pinned original preparation/ACK has been verified. */
record RecoveryTemporaryRestoreMaterial(RecoverySourceIdentity identity, JsonNode origin, JsonNode claims,
        JsonNode manifest, Map<String, JsonNode> content, RecoverySourceTemporaryActivation.Proof activation) {
    static RecoveryTemporaryRestoreMaterial read(RecoveryPinnedSource source,
            RecoveryOriginalTemporaryPreparation.Proof proof, RecoveryReceiptKeys keys) {
        require(source.identity().sourceKind().equals("TEMP") && source.identity().workRound() == 1, "RECOVERY_TEMP_RESTORE_KIND");
        var origin = SubmissionJson.parse(source.original("origin"));
        var claims = new RecoverySourceAuthority(keys).verify(source);
        var manifest = SubmissionJson.parse(text(origin.path("preparationReceipt"), "manifest"));
        return new RecoveryTemporaryRestoreMaterial(source.identity(), origin, claims, manifest,
            RecoveryPreparationParts.read(manifest, origin.path("parts"), true), proof.activation());
    }

    JsonNode receipt() { return origin.path("preparationReceipt"); }
    String signedAck() { return text(SubmissionJson.parse(text(origin.path("activation"), "receipt")), "signedAck"); }
    String keyId() {
        String header = text(receipt(), "offlineToken").split("\\.", -1)[0];
        return text(SubmissionJson.parse(Base64.getUrlDecoder().decode(header)), "kid");
    }
    byte[] compressed(String name) { return Base64.getDecoder().decode(text(origin.path("parts"), name)); }
}
