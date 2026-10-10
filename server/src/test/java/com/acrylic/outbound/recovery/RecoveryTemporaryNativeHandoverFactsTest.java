package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryTemporaryNativeHandoverFactsTest {
    @Test void bindsReceivedTerminalToTheExactFrozenPayloadHash() throws Exception {
        var f=new RecoveryTemporaryNativeHandoverFixture(UUID.randomUUID());
        var received=f.changed("terminal",terminal -> terminal.put("payloadPresent",true).put("payloadSha256",f.archive.provenance().payloadSha256()));
        var facts=RecoveryTemporaryNativeHandoverFacts.read(received,f.input);
        assertThat(facts.canonicalOrderId()).isEqualTo(f.archive.source().canonicalOrderId());
        assertThat(facts.ordinaryWritesAllowed()).isFalse(); assertThat(facts.localDeletionAllowed()).isFalse();
    }

    @ParameterizedTest @ValueSource(strings={"null","wrong","unreceivedHash"})
    void refusesInconsistentReceivedTerminalHash(String kind) throws Exception {
        var f=new RecoveryTemporaryNativeHandoverFixture(UUID.randomUUID());
        var received=f.changed("terminal",terminal -> {
            terminal.put("payloadPresent",!kind.equals("unreceivedHash"));
            if(kind.equals("null")) terminal.putNull("payloadSha256"); else terminal.put("payloadSha256","b".repeat(64));
        });
        assertThatThrownBy(() -> RecoveryTemporaryNativeHandoverFacts.read(received,f.input)).hasMessage("RECOVERY_NATIVE_BYTES_MISMATCH");
    }
    @Test void requiresOriginalVerificationRowDigest() throws Exception {
        var f=new RecoveryTemporaryNativeHandoverFixture(UUID.randomUUID());
        ((ObjectNode)f.input.path("archive")).put("verificationSha256","invalid");
        assertThatThrownBy(() -> RecoveryTemporaryNativeHandoverFacts.read(f.archive,f.input))
            .hasMessage("RECOVERY_NATIVE_VERIFICATION_HASH");
    }

    @Test void bindsNativeIntegerStopAtTheStopBoundary() throws Exception {
        var f = new RecoveryTemporaryNativeHandoverFixture(UUID.randomUUID());
        var facts = RecoveryTemporaryNativeHandoverFacts.read(f.archive, f.input);
        var stop = SubmissionJson.parse(f.archive.stopProof()).path("core");
        assertThat(stop.path("auditThroughSeq").isIntegralNumber()).isTrue();
        assertThat(facts.auditThroughSeq()).isEqualTo(stop.path("auditThroughSeq").longValue());
        assertThat(facts.auditThroughSeq()).isEqualTo(f.evidence.frozen.path("events").size() + 1);
        assertThat(facts.canonicalOrderId()).isEqualTo(f.archive.source().canonicalOrderId());
        assertThat(facts.ordinaryWritesAllowed()).isFalse(); assertThat(facts.localDeletionAllowed()).isFalse();
    }

    @Test void refusesUnknownCanonicalWithoutInferringIt() throws Exception {
        var f = new RecoveryTemporaryNativeHandoverFixture(null);
        assertThatThrownBy(() -> RecoveryTemporaryNativeHandoverFacts.read(f.archive, f.input))
            .hasMessage("RECOVERY_NATIVE_CANONICAL_REQUIRED");
    }

    @ParameterizedTest @ValueSource(strings = {"setSha256", "sourceEvidenceSetId", "captureHighWatermark", "auditThroughSeq", "orderScanHighWatermark"})
    void refusesChangedArchiveBinding(String field) throws Exception {
        var f = new RecoveryTemporaryNativeHandoverFixture(UUID.randomUUID());
        var value = (ObjectNode)f.input.path("archive");
        value.put(field, field.equals("setSha256") ? "b".repeat(64) : field.equals("sourceEvidenceSetId") ? UUID.randomUUID().toString() : "999");
        assertThatThrownBy(() -> RecoveryTemporaryNativeHandoverFacts.read(f.archive, f.input))
            .hasMessage("RECOVERY_NATIVE_ARCHIVE_MISMATCH");
    }

    @ParameterizedTest @ValueSource(strings = {"freezeDeviceId", "authorizationId", "sourceRecoveryEpoch", "workRound", "captureHighWatermark"})
    void refusesForeignPayloadContext(String field) throws Exception {
        var f = new RecoveryTemporaryNativeHandoverFixture(UUID.randomUUID());
        var changed = f.changed("payload", value -> value.put(field, field.endsWith("Id") ? UUID.randomUUID().toString() : "999"));
        assertThatThrownBy(() -> RecoveryTemporaryNativeHandoverFacts.read(changed, f.input))
            .hasMessage("RECOVERY_NATIVE_BYTES_MISMATCH");
    }

    @ParameterizedTest @ValueSource(strings = {"captureHighWatermark", "auditThroughSeq", "workRound", "sourceRecoveryEpoch"})
    void refusesChangedStopWatermarks(String field) throws Exception {
        var f = new RecoveryTemporaryNativeHandoverFixture(UUID.randomUUID());
        var changed = f.changed("stop", value -> value.put(field, 999));
        assertThatThrownBy(() -> RecoveryTemporaryNativeHandoverFacts.read(changed, f.input))
            .hasMessage("RECOVERY_NATIVE_BYTES_MISMATCH");
    }

    @Test void refusesStringEncodedNativeStop() throws Exception {
        var f = new RecoveryTemporaryNativeHandoverFixture(UUID.randomUUID());
        var changed = f.changed("stop", value -> value.put("auditThroughSeq", value.path("auditThroughSeq").asText()));
        assertThatThrownBy(() -> RecoveryTemporaryNativeHandoverFacts.read(changed, f.input))
            .hasMessage("RECOVERY_NATIVE_BYTES_MISMATCH");
    }

    @Test void refusesStopFromAnotherSet() throws Exception {
        var f = new RecoveryTemporaryNativeHandoverFixture(UUID.randomUUID());
        var changed = f.changed("stop", value -> value.put("setHash", "b".repeat(64)));
        assertThatThrownBy(() -> RecoveryTemporaryNativeHandoverFacts.read(changed, f.input))
            .hasMessage("RECOVERY_NATIVE_BYTES_MISMATCH");
    }

    @ParameterizedTest @ValueSource(strings = {"status", "freezeDeviceId", "issuedRecoveryEpoch", "workRound"})
    void refusesChangedTerminal(String field) throws Exception {
        var f = new RecoveryTemporaryNativeHandoverFixture(UUID.randomUUID());
        var changed = f.changed("terminal", value -> value.put(field, field.equals("freezeDeviceId") ? UUID.randomUUID().toString() : "999"));
        assertThatThrownBy(() -> RecoveryTemporaryNativeHandoverFacts.read(changed, f.input))
            .hasMessage("RECOVERY_NATIVE_BYTES_MISMATCH");
    }
}
