package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoverySourceBindingTest {
    @ParameterizedTest @ValueSource(strings = {"installationId", "recoverySessionId", "epochId"})
    void refusesMaterialFromAnotherControlIdentity(String key) throws Exception {
        var f = RecoverySourceFixture.create(); f.manifest.put(key, UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(f::read).hasMessage("RECOVERY_SOURCE_CONTROL_MISMATCH");
    }

    @ParameterizedTest @ValueSource(strings = {"snapshotId", "backupManifestSha256", "possibleLossFrom", "possibleLossThrough"})
    void refusesChangedCutoffOrBackupContext(String key) throws Exception {
        var f = RecoverySourceFixture.create();
        f.manifest.put(key, key.startsWith("possibleLoss") ? "2026-09-19T00:00:00Z" : "c".repeat(64)); f.publish();

        assertThatThrownBy(f::read).hasMessage("RECOVERY_SOURCE_CONTROL_MISMATCH");
    }

    @ParameterizedTest @ValueSource(strings = {"warehouseId", "scopeId", "submissionId", "deviceId", "operatorId",
        "qualificationId", "canonicalOrderId"})
    void refusesDifferentOriginalIdentityEvenWithAnExplicitPin(String key) throws Exception {
        var f = RecoverySourceFixture.create(); ((ObjectNode)f.manifest.path("source")).put(key, UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(f::read).hasMessage("RECOVERY_SOURCE_IDENTITY_MISMATCH");
    }

    @ParameterizedTest @ValueSource(strings = {"sourceRecoveryEpoch", "workRound", "captureHighWatermark", "auditThroughSeq",
        "orderScanHighWatermark"})
    void refusesWrongRoundOrWatermarkEvenWithAnExplicitPin(String key) throws Exception {
        var f = RecoverySourceFixture.create(); ((ObjectNode)f.manifest.path("source")).put(key, "88"); f.publish();

        assertThatThrownBy(f::read).hasMessage("RECOVERY_SOURCE_IDENTITY_MISMATCH");
    }

    @Test void refusesUnknownFieldsInsteadOfIgnoringFutureAuthorityClaims() throws Exception {
        var f = RecoverySourceFixture.create(); f.manifest.put("stockReleased", true); f.publish();

        assertThatThrownBy(f::read).hasMessage("CONTROL_FIELDS");
    }

    @Test void refusesIncompleteInventory() throws Exception {
        var f = RecoverySourceFixture.create(); ((ObjectNode)f.manifest.path("objects")).remove("result"); f.publish();

        assertThatThrownBy(f::read).hasMessage("CONTROL_FIELDS");
    }

    @Test void refusesEmptyOriginalEvenWhenItsDigestIsPinned() throws Exception {
        var f = RecoverySourceFixture.create(); String hash = RecoveryControlFormat.sha256(new byte[0]);
        ((ObjectNode)f.manifest.at("/objects/result")).put("sha256", hash).put("byteCount", "0"); f.publish();
        Files.write(f.materials.resolve(hash + ".bin"), new byte[0]);

        assertThatThrownBy(f::read).hasMessage("RECOVERY_SOURCE_OBJECT_SIZE");
    }

    @Test void refusesLargerOriginalThanDeclared() throws Exception {
        var f = RecoverySourceFixture.create(); ((ObjectNode)f.manifest.at("/objects/payload")).put("byteCount", "1"); f.publish();

        assertThatThrownBy(f::read).hasMessage("RECOVERY_SOURCE_OBJECT_MISMATCH");
    }
}
