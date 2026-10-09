package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.Objects;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Original TEMP policy and plan semantics only; ACK, current access and restored stock remain separate. */
public final class RecoverySourceTemporaryBaseline {
    private final RecoverySourcePreparation preparation;

    public RecoverySourceTemporaryBaseline(RecoveryReceiptKeys keys) {
        preparation = new RecoverySourcePreparation(Objects.requireNonNull(keys));
    }

    public Proof verify(RecoveryPinnedSource source) {
        require(source != null && source.identity().sourceKind().equals("TEMP"), "RECOVERY_TEMP_BASELINE_SOURCE");
        try {
            var proven = preparation.verify(source);
            var origin = SubmissionJson.parse(source.original("origin"));
            var manifest = SubmissionJson.parse(text(origin.path("preparationReceipt"), "manifest"));
            var parts = RecoveryPreparationParts.read(manifest, origin.path("parts"), true);
            var payload = SubmissionJson.parse(source.original("payload"));
            RecoveryTemporaryMaterial.verify(manifest, parts, payload);
            RecoveryTemporaryPlan.verify(source.identity(), payload, parts.get("mapping"));
            return new Proof(proven, text(payload, "planSha256"));
        } catch (RecoveryControlException refusal) { throw refusal; }
        catch (RuntimeException invalid) { throw new RecoveryControlException("RECOVERY_TEMP_BASELINE_INVALID", invalid); }
    }

    public record Proof(RecoverySourcePreparation.Proof preparation, String planSha256) { }
}
