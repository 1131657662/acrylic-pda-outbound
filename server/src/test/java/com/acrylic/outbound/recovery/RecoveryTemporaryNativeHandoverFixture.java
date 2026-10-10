package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;

/** Original E2A encoding from existing development fixtures, with an explicitly synthetic canonical identity. */
final class RecoveryTemporaryNativeHandoverFixture {
    final RecoveryOriginalEvidenceFixture evidence;
    final RecoveryRecoveredTemporaryArchive archive;
    final ObjectNode input;

    RecoveryTemporaryNativeHandoverFixture(UUID canonical) throws Exception {
        this(identity(canonical), UUID.randomUUID());
    }

    RecoveryTemporaryNativeHandoverFixture(RecoverySourceIdentity source, UUID setId) throws Exception {
        var state = new RecoverySourceStateFixture(RecoverySourceFixture.original(source));
        var activation = new RecoveryTemporaryActivationFixture(new RecoveryTemporaryBaselineFixture(new RecoveryPreparationFixture(state)));
        evidence = new RecoveryOriginalEvidenceFixture(new RecoveryInitialFixture(activation.baseline.prep), ignored -> { });
        new RecoveryOriginalEvidence().read(evidence.request());
        var original = new RecoveryUnsubmittedOccupancyFixture(evidence, setId,
            new RecoveryDeviceRegistrationCapture.Database(UUID.randomUUID(), "synthetic_native", "1"));
        var expected = RecoveryUnsubmittedEvidence.expected(evidence.request());
        archive = new RecoveryRecoveredTemporaryArchive(evidence.identity, original.setId, UUID.randomUUID(),
            new RecoveryRecoveredTemporaryArchive.Provenance("c".repeat(64), "d".repeat(64), "e".repeat(64),
                "f".repeat(64), "a".repeat(64), expected.proof().setHash(), expected.proof().payloadSha256()),
            original.initial.source().original("payload"), original.initial.source().original("result"), expected.stopProofBytes());
        input = input(archive);
    }

    private static RecoverySourceIdentity identity(UUID canonical) {
        UUID device = UUID.randomUUID();
        return new RecoverySourceIdentity("TEMP", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), device,
            UUID.randomUUID(), UUID.randomUUID(), canonical, device, UUID.randomUUID(), "1", 1, 1, 5, 1);
    }

    static ObjectNode input(RecoveryRecoveredTemporaryArchive archive) {
        var source = archive.source(); var value = SubmissionJson.object(); var s = value.putObject("source");
        s.put("warehouseId", source.warehouseId().toString()).put("scopeId", source.scopeId().toString())
            .put("submissionId", source.submissionId().toString()).put("deviceId", source.deviceId().toString())
            .put("canonicalOrderId", source.canonicalOrderId() == null ? UUID.randomUUID().toString() : source.canonicalOrderId().toString())
            .put("operatorId", source.operatorId().toString()).put("authorizationId", source.qualificationId().toString())
            .put("creatorDeviceId", source.creatorDeviceId().toString()).put("clientOrderId", source.clientOrderId().toString())
            .put("sourceRecoveryEpoch", source.sourceRecoveryEpoch()).put("workRound", Long.toString(source.workRound()));
        value.putObject("archive").put("sourceEvidenceSetId", archive.evidenceSetId().toString())
            .put("verificationId", UUID.randomUUID().toString()).put("verificationSha256", "0".repeat(64))
            .put("setSha256", archive.provenance().setSha256())
            .put("captureHighWatermark", Long.toString(source.captureHighWatermark()))
            .put("auditThroughSeq", Long.toString(source.auditThroughSeq()))
            .put("orderScanHighWatermark", Long.toString(source.orderScanHighWatermark()))
            .put("status", "VERIFIED_COMPLETE_AT_STOP").put("mayResumeScanning", false).put("stockReleased", false);
        return value.put("terminalStatus", "ABORTED");
    }

    RecoveryRecoveredTemporaryArchive changed(String kind, java.util.function.Consumer<ObjectNode> mutation) {
        var payload = SubmissionJson.parse(archive.payload()); var terminal = SubmissionJson.parse(archive.terminalReceipt());
        var stop = SubmissionJson.parse(archive.stopProof());
        mutation.accept((ObjectNode)(kind.equals("payload") ? payload : kind.equals("stop") ? stop.path("core") : terminal));
        var p = archive.provenance();
        var provenance = new RecoveryRecoveredTemporaryArchive.Provenance(p.controlRecordSha256(), p.sourceManifestSha256(),
            p.rowsSha256(), p.occupancySha256(), p.registrationSha256(), p.setSha256(), SubmissionJson.sha(payload));
        return new RecoveryRecoveredTemporaryArchive(archive.source(), archive.evidenceSetId(), archive.stopObjectId(), provenance,
            RecoveryOriginalEvidenceFixture.bytes(payload), RecoveryOriginalEvidenceFixture.bytes(terminal), RecoveryOriginalEvidenceFixture.bytes(stop));
    }
}
