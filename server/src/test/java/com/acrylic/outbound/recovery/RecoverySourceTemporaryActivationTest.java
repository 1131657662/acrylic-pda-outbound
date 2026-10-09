package com.acrylic.outbound.recovery;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoverySourceTemporaryActivationTest {
    @Test void provesNativeNanosecondDeclarationWithDelayedAckWithoutRenewingWork() throws Exception {
        var f = new RecoveryTemporaryActivationFixture();

        var proof = f.reader().verify(f.baseline.prep.read());

        assertThat(proof.activatedAt()).isEqualTo(Instant.parse("2026-09-20T00:01:00.123456789Z"));
        assertThat(proof.acknowledgedAt()).isAfter(Instant.parse("2026-09-22T00:00:00Z"));
        assertThat(f.baseline.prep.read().ordinaryWritesAllowed()).isFalse(); assertThat(f.baseline.prep.read().localDeletionAllowed()).isFalse();
    }

    @Test void refusesMissingOriginalActivation() throws Exception {
        var f = new RecoveryTemporaryBaselineFixture();

        var reader = new RecoverySourceTemporaryActivation(new RecoveryReceiptKeys(java.util.List.of(f.prep.state.authority.key.pair.getPublic().getEncoded())));

        assertThatThrownBy(() -> reader.verify(f.prep.read())).hasMessage("RECOVERY_TEMP_ACTIVATION_REQUIRED");
    }

    @ParameterizedTest @ValueSource(strings = {"authorizationId", "userId", "deviceId", "bundleId"})
    void refusesForeignSignedAckIdentity(String field) throws Exception {
        var f = new RecoveryTemporaryActivationFixture(); f.proof.put(field, UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.baseline.prep.read())).hasMessage("RECOVERY_TEMP_ACTIVATION_IDENTITY");
    }

    @Test void refusesWrongHistoricalRecoveryEpoch() throws Exception {
        var f = new RecoveryTemporaryActivationFixture(); f.proof.put("serverRecoveryEpoch", "2"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.baseline.prep.read())).hasMessage("RECOVERY_TEMP_ACTIVATION_IDENTITY");
    }

    @Test void refusesAckSignedForPreparationPurpose() throws Exception {
        var f = new RecoveryTemporaryActivationFixture(); f.proof.put("aud", "acrylic-pda-temporary-preparation"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.baseline.prep.read())).hasMessage("RECOVERY_TEMP_ACTIVATION_INVALID");
    }

    @Test void refusesModifiedOriginalCommandEvenWithTheSameStoredMillisecondTime() throws Exception {
        var f = new RecoveryTemporaryActivationFixture(); f.command.put("activatedAt", "2026-09-20T00:01:00.123999999Z"); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.baseline.prep.read())).hasMessage("RECOVERY_TEMP_ACTIVATION_DIGEST");
    }

    @Test void refusesOuterReceiptDifferentFromSignedOriginal() throws Exception {
        var f = new RecoveryTemporaryActivationFixture(); f.receipt.put("acknowledgedAt", "2026-09-24T00:00:00Z"); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.baseline.prep.read())).hasMessage("RECOVERY_TEMP_ACTIVATION_RECEIPT");
    }

    @Test void refusesActivationAtTheOriginalExpiry() throws Exception {
        var f = new RecoveryTemporaryActivationFixture(); f.command.put("activatedAt", "2026-09-22T00:00:00Z");
        f.proof.put("activatedAt", "2026-09-22T00:00:00Z").put("commandSha256", f.digest()); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.baseline.prep.read())).hasMessage("RECOVERY_TEMP_ACTIVATION_WINDOW");
    }

    @Test void refusesActivationAfterAnOriginalConfirmation() throws Exception {
        var f = new RecoveryTemporaryActivationFixture(); f.command.put("activatedAt", "2026-09-20T00:30:01Z");
        f.proof.put("activatedAt", "2026-09-20T00:30:01Z").put("commandSha256", f.digest()); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.baseline.prep.read())).hasMessage("RECOVERY_TEMP_ACTIVATION_SAVE");
    }

    @Test void refusesAcknowledgmentEarlierThanNativeClockTolerance() throws Exception {
        var f = new RecoveryTemporaryActivationFixture(); f.proof.put("acknowledgedAt", "2026-09-19T23:55:59Z"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.baseline.prep.read())).hasMessage("RECOVERY_TEMP_ACTIVATION_TIME");
    }

    @Test void refusesUnexpectedCommandField() throws Exception {
        var f = new RecoveryTemporaryActivationFixture(); f.command.put("newWorkAllowed", true); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.baseline.prep.read())).hasMessage("CONTROL_FIELDS");
    }
}
