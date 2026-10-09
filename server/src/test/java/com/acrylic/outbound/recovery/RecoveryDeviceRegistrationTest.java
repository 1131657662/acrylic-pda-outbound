package com.acrylic.outbound.recovery;

import com.acrylic.outbound.devices.DeviceCrypto;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryDeviceRegistrationTest {
    @Test void independentlyPinnedOriginalKeyVerifiesTheNativeSource() throws Exception {
        var f = new RecoveryDeviceRegistrationFixture();

        var registration = f.read(); var evidence = f.nativeSource.evidence;
        var proof = f.nativeSource.initial.reader().readTemporary(f.nativeSource.initial.request(),
            evidence.request(registration.trustedKey(), evidence.identity));

        assertThat(proof.retainedRowCount()).isEqualTo(1);
        assertThat(registration.originalSha256()).isEqualTo(f.pin);
        assertThat(registration.ordinaryWritesAllowed()).isFalse(); assertThat(registration.localDeletionAllowed()).isFalse();
        assertThat(f.device().path("keyTextSha256").asText()).isNotEqualTo(RecoveryControlFormat.sha256(registration.trustedKey().getEncoded()));
    }

    @Test void originalCompletedPairingApprovalIsRechecked() throws Exception {
        var f = new RecoveryDeviceRegistrationFixture(); f.paired();

        assertThat(f.read().trustedKey().getEncoded()).containsExactly(f.nativeSource.evidence.key.getPublic().getEncoded());
    }

    @Test void disabledObservationStillProvidesAKeyWithoutRestoringWorkPermission() throws Exception {
        var f = new RecoveryDeviceRegistrationFixture(); f.device().put("status", "DISABLED"); f.publish();

        assertThat(f.read().ordinaryWritesAllowed()).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"mysqlServerUuid", "databaseSchema", "sourceRecoveryEpoch", "observationKind"})
    void independentlyExpectedDatabaseAndObservationPurposeCannotBeBorrowed(String field) throws Exception {
        var f = new RecoveryDeviceRegistrationFixture();
        f.value.put(field, field.equals("mysqlServerUuid") ? UUID.randomUUID().toString() : field.equals("sourceRecoveryEpoch") ? "2" : "other");
        f.publish();

        assertThatThrownBy(f::read).isInstanceOf(RecoveryControlException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"deviceId", "warehouseId"})
    void anotherOriginalIdentityCannotDonateAKey(String field) throws Exception {
        var f = new RecoveryDeviceRegistrationFixture(); f.device().put(field, UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(f::read).hasMessage("RECOVERY_DEVICE_REGISTRATION_IDENTITY");
    }

    @Test void snapshotTakenAfterTheOriginalQualificationCannotBackdateRegistration() throws Exception {
        var f = new RecoveryDeviceRegistrationFixture(); f.value.put("observedAt", "2026-09-20T00:00:00.001Z"); f.publish();

        assertThatThrownBy(f::read).hasMessage("RECOVERY_DEVICE_REGISTRATION_TOO_LATE");
    }

    @ParameterizedTest @ValueSource(strings = {"registeredAt", "deviceVersion", "status", "temporaryOrderEnabled"})
    void incompleteOrInconsistentRegistrationStateFailsClosed(String field) throws Exception {
        var f = new RecoveryDeviceRegistrationFixture();
        f.device().put(field, field.equals("registeredAt") ? "2026-09-20T00:00:00Z" : field.equals("deviceVersion") ? "0" : "invalid");
        f.publish();

        assertThatThrownBy(f::read).isInstanceOf(RecoveryControlException.class);
    }

    @Test void derHashCannotReplaceTheOriginalTextHash() throws Exception {
        var f = new RecoveryDeviceRegistrationFixture();
        f.device().put("keyTextSha256", RecoveryControlFormat.sha256(f.nativeSource.evidence.key.getPublic().getEncoded())); f.publish();

        assertThatThrownBy(f::read).hasMessage("RECOVERY_DEVICE_REGISTRATION_HASH");
    }

    @Test void anotherAlgorithmCannotBePresentedAsOriginalP256() throws Exception {
        var f = new RecoveryDeviceRegistrationFixture(); var generator = java.security.KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        String encoded = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
        f.device().put("publicKey", encoded).put("keyTextSha256", DeviceCrypto.sha(encoded)); f.publish();

        assertThatThrownBy(f::read).hasMessage("RECOVERY_DEVICE_REGISTRATION_KEY");
    }

    @ParameterizedTest @ValueSource(strings = {"status", "deviceId", "publicKey", "keyTextSha256", "approvedBy", "approvedAuthVersion", "approvalSha256"})
    void damagedOrIncompletePairingMustNotProvideTrust(String field) throws Exception {
        var f = new RecoveryDeviceRegistrationFixture(); f.paired(); f.pairing().put(field, "invalid"); f.publish();

        assertThatThrownBy(f::read).isInstanceOf(RecoveryControlException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"applicationId", "warehouseId", "keySha256", "serverRecoveryEpoch", "name"})
    void originalPairingApprovalCannotBeChanged(String field) throws Exception {
        var f = new RecoveryDeviceRegistrationFixture(); f.paired();
        ((com.fasterxml.jackson.databind.node.ObjectNode)f.pairing().path("approval")).put(field,
            field.endsWith("Id") ? UUID.randomUUID().toString() : field.equals("serverRecoveryEpoch") ? "2" : "changed"); f.publish();

        assertThatThrownBy(f::read).hasMessage("RECOVERY_DEVICE_REGISTRATION_APPROVAL");
    }

    @Test void unknownPinAndChangedOriginalBytesAreRejected() throws Exception {
        var f = new RecoveryDeviceRegistrationFixture(); var request = f.request();
        byte[] bytes = Files.readAllBytes(f.materials.resolve(f.pin + ".json")); bytes[0] ^= 1;
        Files.write(f.materials.resolve(f.pin + ".json"), bytes);

        assertThatThrownBy(() -> f.reader().read(request)).hasMessage("RECOVERY_DEVICE_REGISTRATION_PIN_MISMATCH");
        assertThatThrownBy(() -> f.reader().read(new RecoveryDeviceRegistration.Request(f.materials, null, f.expected())))
            .hasMessage("RECOVERY_DEVICE_REGISTRATION_PIN");
    }

    @Test void aRegistrationCopiedIntoTheRestoreSetCannotSupplyTrust() throws Exception {
        var f = new RecoveryDeviceRegistrationFixture(); var restore = f.source.control.location().restoreRoots().getFirst();
        Files.createDirectories(restore); Files.copy(f.materials.resolve(f.pin + ".json"), restore.resolve(f.pin + ".json"));

        assertThatThrownBy(() -> f.reader().read(new RecoveryDeviceRegistration.Request(restore, f.pin, f.expected())))
            .hasMessage("RECOVERY_SOURCE_INSIDE_RESTORE_SET");
    }
}
