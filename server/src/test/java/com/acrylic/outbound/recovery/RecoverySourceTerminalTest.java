package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoverySourceTerminalTest {
    @ParameterizedTest @ValueSource(strings = {"ABORTED", "SUCCEEDED"})
    void verifiesPinnedTerminalWithoutRewritingOrReplayingIt(String status) throws Exception {
        var f = RecoverySourceFixture.create(); var key = new RecoveryReceiptFixture(); key.publish(f, key.receipt(f, status));
        var source = f.read();

        var receipt = new RecoverySourceTerminal(key.verifier).verify(source);

        assertThat(receipt.path("status").asText()).isEqualTo(status);
        assertThat(receipt.path("submissionId").asText()).isEqualTo(f.identity.submissionId().toString());
        assertThat(source.ordinaryWritesAllowed()).isFalse(); assertThat(source.localDeletionAllowed()).isFalse();
    }

    @Test void preservesTemporaryCreatorIndependentlyOfTheCurrentFreezeDevice() throws Exception {
        var f = RecoverySourceFixture.temporary(); var key = new RecoveryReceiptFixture(); key.publish(f, key.receipt(f, "ABORTED"));

        var receipt = new RecoverySourceTerminal(key.verifier).verify(f.read());

        assertThat(receipt.at("/source/creatorDeviceId").asText()).isEqualTo(f.identity.creatorDeviceId().toString());
        assertThat(receipt.at("/source/creatorDeviceId").asText()).isNotEqualTo(f.identity.deviceId().toString());
    }

    @ParameterizedTest @ValueSource(strings = {"creatorDeviceId", "clientOrderId"})
    void rejectsDifferentTemporarySourceDespiteAValidServerSignature(String field) throws Exception {
        var f = RecoverySourceFixture.temporary(); var key = new RecoveryReceiptFixture(); var receipt = key.receipt(f, "ABORTED");
        ((ObjectNode)receipt.path("source")).put(field, UUID.randomUUID().toString()); key.publish(f, receipt);

        assertThatThrownBy(() -> new RecoverySourceTerminal(key.verifier).verify(f.read())).hasMessage("RECOVERY_SOURCE_RESULT_MISMATCH");
    }

    @ParameterizedTest @ValueSource(strings = {"submissionId", "freezeDeviceId"})
    void rejectsDifferentOriginalIdDespiteAValidServerSignature(String field) throws Exception {
        var f = RecoverySourceFixture.create(); var key = new RecoveryReceiptFixture(); var receipt = key.receipt(f, "ABORTED");
        receipt.put(field, UUID.randomUUID().toString()); key.publish(f, receipt);

        assertThatThrownBy(() -> new RecoverySourceTerminal(key.verifier).verify(f.read())).hasMessage("RECOVERY_SOURCE_RESULT_MISMATCH");
    }

    @ParameterizedTest @ValueSource(strings = {"issuedRecoveryEpoch", "workRound"})
    void rejectsDifferentOriginalRoundDespiteAValidServerSignature(String field) throws Exception {
        var f = RecoverySourceFixture.create(); var key = new RecoveryReceiptFixture(); var receipt = key.receipt(f, "ABORTED");
        receipt.put(field, "77"); key.publish(f, receipt);

        assertThatThrownBy(() -> new RecoverySourceTerminal(key.verifier).verify(f.read())).hasMessage("RECOVERY_SOURCE_RESULT_MISMATCH");
    }

    @ParameterizedTest @ValueSource(strings = {"mayResumeScanning", "stockReleased", "localDeletionAllowed"})
    void refusesAnyReleaseClaimEvenIfSigned(String field) throws Exception {
        var f = RecoverySourceFixture.create(); var key = new RecoveryReceiptFixture(); var receipt = key.receipt(f, "ABORTED");
        receipt.put(field, true); key.publish(f, receipt);

        assertThatThrownBy(() -> new RecoverySourceTerminal(key.verifier).verify(f.read())).hasMessage("RECOVERY_SOURCE_RESULT_RELEASE");
    }

    @Test void cannotTreatReviewRequiredAsATerminal() throws Exception {
        var f = RecoverySourceFixture.create(); var key = new RecoveryReceiptFixture(); key.publish(f, key.receipt(f, "REVIEW_REQUIRED"));

        assertThatThrownBy(() -> new RecoverySourceTerminal(key.verifier).verify(f.read())).hasMessage("RECOVERY_SOURCE_RESULT_OPEN");
    }

    @Test void rejectsPayloadFromAnotherOriginalDespiteAValidReceipt() throws Exception {
        var f = RecoverySourceFixture.create(); var key = new RecoveryReceiptFixture(); var receipt = key.receipt(f, "ABORTED");
        receipt.put("payloadSha256", "a".repeat(64)); key.publish(f, receipt);

        assertThatThrownBy(() -> new RecoverySourceTerminal(key.verifier).verify(f.read())).hasMessage("RECOVERY_SOURCE_RESULT_PAYLOAD");
    }

    @Test void keepsPayloadAbsentAbortWithoutInventingASubmittedBody() throws Exception {
        var f = RecoverySourceFixture.create(); var key = new RecoveryReceiptFixture(); var receipt = key.receipt(f, "ABORTED");
        receipt.putNull("payloadSha256").put("payloadPresent", false); key.publish(f, receipt);

        assertThat(new RecoverySourceTerminal(key.verifier).verify(f.read()).path("payloadSha256").isNull()).isTrue();
    }

    @Test void rejectsSuccessWithoutOriginalPayloadBinding() throws Exception {
        var f = RecoverySourceFixture.create(); var key = new RecoveryReceiptFixture(); var receipt = key.receipt(f, "SUCCEEDED");
        receipt.putNull("payloadSha256").put("payloadPresent", false); key.publish(f, receipt);

        assertThatThrownBy(() -> new RecoverySourceTerminal(key.verifier).verify(f.read())).hasMessage("RECOVERY_SOURCE_RESULT_PAYLOAD");
    }

    @Test void rejectsWrongSuccessHighWatermarkDespiteAValidSignature() throws Exception {
        var f = RecoverySourceFixture.create(); var key = new RecoveryReceiptFixture(); var receipt = key.receipt(f, "SUCCEEDED");
        receipt.put("captureHighWatermark", "99"); key.publish(f, receipt);

        assertThatThrownBy(() -> new RecoverySourceTerminal(key.verifier).verify(f.read())).hasMessage("RECOVERY_SOURCE_RESULT_MISMATCH");
    }

    @Test void bindsLocalFrozenOriginalWithoutDeclaringItSubmitted() throws Exception {
        var f = RecoverySourceFixture.temporary(); var key = new RecoveryReceiptFixture(); var receipt = key.receipt(f, "ABORTED");
        receipt.put("localFrozenPayloadSha256", receipt.path("payloadSha256").asText()).putNull("payloadSha256").put("payloadPresent", false);
        key.publish(f, receipt);

        assertThat(new RecoverySourceTerminal(key.verifier).verify(f.read()).path("payloadPresent").asBoolean()).isFalse();
    }

    @Test void rejectsLocalFrozenOriginalFromAnotherPayload() throws Exception {
        var f = RecoverySourceFixture.temporary(); var key = new RecoveryReceiptFixture(); var receipt = key.receipt(f, "ABORTED");
        receipt.put("localFrozenPayloadSha256", "c".repeat(64)).putNull("payloadSha256").put("payloadPresent", false); key.publish(f, receipt);

        assertThatThrownBy(() -> new RecoverySourceTerminal(key.verifier).verify(f.read())).hasMessage("RECOVERY_SOURCE_RESULT_PAYLOAD");
    }

    @Test void requiresExplicitPayloadAbsence() throws Exception {
        var f = RecoverySourceFixture.create(); var key = new RecoveryReceiptFixture(); var receipt = key.receipt(f, "ABORTED");
        receipt.remove("payloadSha256"); receipt.put("payloadPresent", false); key.publish(f, receipt);

        assertThatThrownBy(() -> new RecoverySourceTerminal(key.verifier).verify(f.read())).hasMessage("RECOVERY_SOURCE_RESULT_PAYLOAD");
    }
}
