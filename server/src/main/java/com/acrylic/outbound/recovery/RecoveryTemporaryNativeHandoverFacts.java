package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/**
 * Binds an explicitly retained native TEMP handover context to a recovered archive.
 * This is a read-only proof value; it never creates an order, verification or admission.
 */
public final class RecoveryTemporaryNativeHandoverFacts {
    private RecoveryTemporaryNativeHandoverFacts() { }

    static Facts read(RecoveryRecoveredTemporaryArchive archive, JsonNode input) {
        require(archive != null && input != null, "RECOVERY_NATIVE_FACTS_REQUIRED");
        fields(input, "source archive terminalStatus");
        Source source = source(input.path("source"));
        ArchiveFacts facts = archive(input.path("archive"));
        String terminalStatus = text(input, "terminalStatus");
        require("ABORTED".equals(terminalStatus), "RECOVERY_NATIVE_TERMINAL");
        RecoverySourceIdentity original = archive.source();
        require("TEMP".equals(original.sourceKind()) && original.canonicalOrderId() != null,
            "RECOVERY_NATIVE_CANONICAL_REQUIRED");
        bindSource(source, original);
        bindArchive(facts, archive, original);
        bindBytes(archive, terminalStatus);
        return new Facts(original.canonicalOrderId(), facts.evidenceSetId(), facts.verificationId(),
            facts.verificationSha256(), facts.setSha256(), facts.captureHighWatermark(), facts.auditThroughSeq(), facts.orderScanHighWatermark());
    }

    static Verified readVerified(RecoveryRecoveredTemporaryArchive archive, JsonNode input, JdbcTemplate jdbc) {
        var facts = read(archive, input);
        return new Verified(facts, new RecoveryTemporaryNativeVerification(jdbc).verify(archive, facts));
    }

    private static Source source(JsonNode value) {
        fields(value, "warehouseId scopeId submissionId canonicalOrderId deviceId operatorId authorizationId creatorDeviceId clientOrderId sourceRecoveryEpoch workRound");
        return new Source(uuid(value, "warehouseId"), uuid(value, "scopeId"), uuid(value, "submissionId"),
            uuid(value, "canonicalOrderId"), uuid(value, "deviceId"), uuid(value, "operatorId"), uuid(value, "authorizationId"),
            uuid(value, "creatorDeviceId"), uuid(value, "clientOrderId"), text(value, "sourceRecoveryEpoch"), revision(value, "workRound"));
    }

    private static ArchiveFacts archive(JsonNode value) {
        fields(value, "sourceEvidenceSetId verificationId verificationSha256 setSha256 captureHighWatermark auditThroughSeq orderScanHighWatermark status mayResumeScanning stockReleased");
        require(value.path("mayResumeScanning").isBoolean() && value.path("stockReleased").isBoolean(), "RECOVERY_NATIVE_ARCHIVE_FIELDS");
        return new ArchiveFacts(uuid(value, "sourceEvidenceSetId"), uuid(value, "verificationId"), text(value, "verificationSha256"), text(value, "setSha256"),
            revision(value, "captureHighWatermark"), revision(value, "auditThroughSeq"), revision(value, "orderScanHighWatermark"),
            text(value, "status"), value.path("mayResumeScanning").asBoolean(false), value.path("stockReleased").asBoolean(false));
    }

    private static void bindSource(Source actual, RecoverySourceIdentity original) {
        require(actual.warehouseId().equals(original.warehouseId()) && actual.scopeId().equals(original.scopeId())
            && actual.submissionId().equals(original.submissionId()) && actual.canonicalOrderId().equals(original.canonicalOrderId())
            && actual.deviceId().equals(original.deviceId()) && actual.operatorId().equals(original.operatorId())
            && actual.authorizationId().equals(original.qualificationId()) && actual.creatorDeviceId().equals(original.creatorDeviceId())
            && actual.clientOrderId().equals(original.clientOrderId()) && actual.sourceRecoveryEpoch().equals(original.sourceRecoveryEpoch())
            && actual.workRound() == original.workRound(), "RECOVERY_NATIVE_SOURCE_MISMATCH");
    }

    private static void bindArchive(ArchiveFacts actual, RecoveryRecoveredTemporaryArchive archive, RecoverySourceIdentity original) {
        var provenance = archive.provenance();
        require(actual.evidenceSetId().equals(archive.evidenceSetId()) && actual.setSha256().equals(provenance.setSha256())
            && actual.captureHighWatermark() == original.captureHighWatermark()
            && actual.auditThroughSeq() == original.auditThroughSeq()
            && actual.orderScanHighWatermark() == original.orderScanHighWatermark()
            && "VERIFIED_COMPLETE_AT_STOP".equals(actual.status()) && !actual.mayResumeScanning() && !actual.stockReleased(),
            "RECOVERY_NATIVE_ARCHIVE_MISMATCH");
    }

    private static void bindBytes(RecoveryRecoveredTemporaryArchive archive, String terminalStatus) {
        JsonNode payload = SubmissionJson.parse(archive.payload());
        JsonNode stop = SubmissionJson.parse(archive.stopProof());
        JsonNode terminal = SubmissionJson.parse(archive.terminalReceipt());
        require(SubmissionJson.sha(payload).equals(archive.provenance().payloadSha256()), "RECOVERY_NATIVE_BYTES_MISMATCH");
        bindPayload(payload, archive.source()); bindStop(stop, archive.source(), archive.provenance().setSha256());
        bindTerminal(terminal, archive, terminalStatus);
    }

    private static void bindPayload(JsonNode payload, RecoverySourceIdentity source) {
        require(payload.path("submissionId").asText().equals(source.submissionId().toString())
            && payload.path("authorizationId").asText().equals(source.qualificationId().toString())
            && payload.path("freezeDeviceId").asText().equals(source.deviceId().toString())
            && payload.path("sourceRecoveryEpoch").asText().equals(source.sourceRecoveryEpoch())
            && revision(payload, "workRound") == source.workRound()
            && revision(payload, "captureHighWatermark") == source.captureHighWatermark(), "RECOVERY_NATIVE_BYTES_MISMATCH");
        var origin = payload.path("source");
        require(origin.path("kind").asText().equals("TEMP")
            && origin.path("creatorDeviceId").asText().equals(source.creatorDeviceId().toString())
            && origin.path("clientOrderId").asText().equals(source.clientOrderId().toString()), "RECOVERY_NATIVE_BYTES_MISMATCH");
    }

    private static void bindStop(JsonNode stop, RecoverySourceIdentity source, String setSha256) {
        var core = stop.path("core");
        require(core.isObject() && core.path("submissionId").asText().equals(source.submissionId().toString())
            && core.path("sourceId").asText().equals(source.qualificationId().toString())
            && core.path("warehouseId").asText().equals(source.warehouseId().toString())
            && core.path("freezeDeviceId").asText().equals(source.deviceId().toString())
            && core.path("sourceRecoveryEpoch").asText().equals(source.sourceRecoveryEpoch())
            && core.path("originalOperatorId").asText().equals(source.operatorId().toString())
            && nativeNumber(core, "workRound") == source.workRound()
            && nativeNumber(core, "sourceRecoveryEpoch") == Long.parseLong(source.sourceRecoveryEpoch())
            && nativeNumber(core, "captureHighWatermark") == source.captureHighWatermark()
            && nativeNumber(core, "auditThroughSeq") == source.auditThroughSeq()
            && core.path("setHash").asText().equals(setSha256), "RECOVERY_NATIVE_BYTES_MISMATCH");
    }

    private static void bindTerminal(JsonNode terminal, RecoveryRecoveredTemporaryArchive archive, String expectedStatus) {
        var source = archive.source();
        require(expectedStatus.equals(terminal.path("status").asText())
            && terminal.path("submissionId").asText().equals(source.submissionId().toString())
            && terminal.path("freezeDeviceId").asText().equals(source.deviceId().toString())
            && terminal.path("issuedRecoveryEpoch").asText().equals(source.sourceRecoveryEpoch())
            && revision(terminal, "workRound") == source.workRound()
            && terminal.path("mayResumeScanning").isBoolean() && !terminal.path("mayResumeScanning").asBoolean()
            && terminal.path("stockReleased").isBoolean() && !terminal.path("stockReleased").asBoolean(),
            "RECOVERY_NATIVE_BYTES_MISMATCH");
        require(terminal.path("source").equals(SubmissionJson.parse(archive.payload()).path("source"))
            && terminal.path("payloadPresent").isBoolean(), "RECOVERY_NATIVE_BYTES_MISMATCH");
        require(terminal.path("payloadPresent").booleanValue()
            ? terminal.path("payloadSha256").isTextual() && terminal.path("payloadSha256").textValue().equals(archive.provenance().payloadSha256())
            : terminal.path("payloadSha256").isNull(), "RECOVERY_NATIVE_BYTES_MISMATCH");
        if (terminal.has("localFrozenPayloadSha256"))
            require(terminal.path("localFrozenPayloadSha256").asText().equals(archive.provenance().payloadSha256()),
                "RECOVERY_NATIVE_BYTES_MISMATCH");
    }

    private static long nativeNumber(JsonNode value, String key) {
        var number = value.path(key);
        require(number.isIntegralNumber() && number.canConvertToLong() && number.longValue() >= 0,
            "RECOVERY_NATIVE_BYTES_MISMATCH");
        return number.longValue();
    }

    public record Facts(UUID canonicalOrderId, UUID evidenceSetId, UUID verificationId, String verificationSha256, String setSha256,
            long captureHighWatermark, long auditThroughSeq, long orderScanHighWatermark) {
        public Facts {
            require(canonicalOrderId != null && evidenceSetId != null && verificationId != null, "RECOVERY_NATIVE_FACTS_ID");
            require(verificationSha256 != null && verificationSha256.matches("[a-f0-9]{64}"), "RECOVERY_NATIVE_VERIFICATION_HASH");
            require(setSha256 != null && setSha256.matches("[a-f0-9]{64}"), "RECOVERY_NATIVE_FACTS_HASH");
            require(captureHighWatermark >= 0 && auditThroughSeq > 0 && orderScanHighWatermark >= 0, "RECOVERY_NATIVE_FACTS_WATERMARK");
        }
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }

    private record Source(UUID warehouseId, UUID scopeId, UUID submissionId, UUID canonicalOrderId, UUID deviceId,
            UUID operatorId, UUID authorizationId, UUID creatorDeviceId, UUID clientOrderId, String sourceRecoveryEpoch, long workRound) { }
    private record ArchiveFacts(UUID evidenceSetId, UUID verificationId, String verificationSha256, String setSha256, long captureHighWatermark,
            long auditThroughSeq, long orderScanHighWatermark, String status, boolean mayResumeScanning, boolean stockReleased) { }
    record Verified(Facts facts, JsonNode verification) { }
}
