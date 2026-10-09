package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.Set;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Reproves the pinned original terminal. Completeness, stock, permission and replay remain separate gates. */
public final class RecoverySourceTerminal {
    private final RecoveryReceiptVerifier receipts;

    public RecoverySourceTerminal(RecoveryReceiptVerifier receipts) { this.receipts = Objects.requireNonNull(receipts); }

    public JsonNode verify(RecoveryPinnedSource source) {
        require(source != null, "RECOVERY_SOURCE_IDENTITY");
        var receipt = receipts.verify(source.original("result"));
        var identity = source.identity();
        require(Set.of("ABORTED", "SUCCEEDED").contains(text(receipt, "status")), "RECOVERY_SOURCE_RESULT_OPEN");
        require(uuid(receipt, "submissionId").equals(identity.submissionId())
            && uuid(receipt, "freezeDeviceId").equals(identity.deviceId())
            && revision(receipt, "workRound") == identity.workRound()
            && text(receipt, "issuedRecoveryEpoch").equals(identity.sourceRecoveryEpoch()), "RECOVERY_SOURCE_RESULT_MISMATCH");
        require(receipt.path("mayResumeScanning").isBoolean() && !receipt.path("mayResumeScanning").booleanValue()
            && receipt.path("stockReleased").isBoolean() && !receipt.path("stockReleased").booleanValue(), "RECOVERY_SOURCE_RESULT_RELEASE");
        if (receipt.has("localDeletionAllowed"))
            require(receipt.path("localDeletionAllowed").isBoolean() && !receipt.path("localDeletionAllowed").booleanValue(),
                "RECOVERY_SOURCE_RESULT_RELEASE");
        requireSource(receipt, identity);
        requirePayload(receipt, source);
        if ("SUCCEEDED".equals(text(receipt, "status")))
            require(revision(receipt, "captureHighWatermark") == identity.captureHighWatermark(), "RECOVERY_SOURCE_RESULT_MISMATCH");
        return receipt;
    }

    private void requirePayload(JsonNode receipt, RecoveryPinnedSource source) {
        require(receipt.has("payloadSha256"), "RECOVERY_SOURCE_RESULT_PAYLOAD");
        if (receipt.hasNonNull("payloadSha256")) {
            var payload = SubmissionJson.parse(source.original("payload"));
            require(text(receipt, "payloadSha256").equals(SubmissionJson.sha(payload)), "RECOVERY_SOURCE_RESULT_PAYLOAD");
            if ("ABORTED".equals(text(receipt, "status")))
                require(receipt.path("payloadPresent").isBoolean() && receipt.path("payloadPresent").booleanValue(),
                    "RECOVERY_SOURCE_RESULT_PAYLOAD");
            return;
        }
        require("ABORTED".equals(text(receipt, "status")) && receipt.path("payloadPresent").isBoolean()
            && !receipt.path("payloadPresent").booleanValue(), "RECOVERY_SOURCE_RESULT_PAYLOAD");
        if (receipt.has("localFrozenPayloadSha256"))
            require(text(receipt, "localFrozenPayloadSha256").equals(SubmissionJson.sha(SubmissionJson.parse(source.original("payload")))),
                "RECOVERY_SOURCE_RESULT_PAYLOAD");
    }

    private void requireSource(JsonNode receipt, RecoverySourceIdentity identity) {
        var source = receipt.path("source");
        require(text(source, "kind").equals(identity.sourceKind()), "RECOVERY_SOURCE_RESULT_MISMATCH");
        if (identity.sourceKind().equals("FORMAL")) {
            fields(source, "kind sourceOrderId");
            require(uuid(source, "sourceOrderId").equals(identity.canonicalOrderId()), "RECOVERY_SOURCE_RESULT_MISMATCH");
        } else {
            fields(source, "kind creatorDeviceId clientOrderId");
            require(uuid(source, "creatorDeviceId").equals(identity.creatorDeviceId())
                && uuid(source, "clientOrderId").equals(identity.clientOrderId()), "RECOVERY_SOURCE_RESULT_MISMATCH");
        }
    }
}
