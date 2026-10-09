package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Historical TEMP ACK only. A delayed receipt neither renews the original save window nor permits new work. */
public final class RecoverySourceTemporaryActivation {
    private final RecoverySourceTemporaryBaseline baseline;
    private final RecoveryTemporaryAckSignature signature;

    public RecoverySourceTemporaryActivation(RecoveryReceiptKeys keys) {
        baseline = new RecoverySourceTemporaryBaseline(Objects.requireNonNull(keys)); signature = new RecoveryTemporaryAckSignature(keys);
    }

    public Proof verify(RecoveryPinnedSource source) {
        var proven = baseline.verify(source);
        try {
            var origin = SubmissionJson.parse(source.original("origin")); var activation = origin.path("activation");
            require(activation.isObject(), "RECOVERY_TEMP_ACTIVATION_REQUIRED"); fields(activation, "command receipt");
            var manifest = SubmissionJson.parse(text(origin.path("preparationReceipt"), "manifest"));
            var command = SubmissionJson.parse(text(activation, "command")); var receipt = SubmissionJson.parse(text(activation, "receipt"));
            var proof = signature.verify(receipt, new RecoveryTemporaryAckSignature.Context(source.identity(), origin.path("preparationReceipt"), command));
            Instant activated = RecoveryPreparationBinding.time(command, "activatedAt");
            require(!activated.isBefore(RecoveryPreparationBinding.time(manifest, "issuedAt"))
                && activated.isBefore(RecoveryPreparationBinding.time(manifest, "expiresAt")), "RECOVERY_TEMP_ACTIVATION_WINDOW");
            require(activated.truncatedTo(ChronoUnit.MILLIS).equals(RecoveryPreparationBinding.time(receipt, "activatedAt")),
                "RECOVERY_TEMP_ACTIVATION_TIME");
            Instant acknowledged = RecoveryPreparationBinding.time(receipt, "acknowledgedAt");
            require(!acknowledged.isBefore(activated.truncatedTo(ChronoUnit.MILLIS).minusSeconds(300)), "RECOVERY_TEMP_ACTIVATION_TIME");
            saves(SubmissionJson.parse(source.original("payload")), activated.truncatedTo(ChronoUnit.MILLIS));
            return new Proof(proven, text(proof, "commandSha256"), activated, acknowledged);
        } catch (RecoveryControlException refusal) { throw refusal; }
        catch (RuntimeException invalid) { throw new RecoveryControlException("RECOVERY_TEMP_ACTIVATION_INVALID", invalid); }
    }

    private void saves(JsonNode payload, Instant activated) {
        for (var capture : SubmissionJson.array(payload, "captures"))
            if (capture.hasNonNull("confirmedAt")) require(!RecoveryPreparationBinding.time(capture, "confirmedAt").isBefore(activated),
                "RECOVERY_TEMP_ACTIVATION_SAVE");
        for (var scan : SubmissionJson.array(payload, "scans"))
            require(!RecoveryPreparationBinding.time(scan, "savedAt").isBefore(activated), "RECOVERY_TEMP_ACTIVATION_SAVE");
    }

    public record Proof(RecoverySourceTemporaryBaseline.Proof baseline, String commandSha256, Instant activatedAt, Instant acknowledgedAt) { }
}
