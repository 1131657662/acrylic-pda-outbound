package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryNativeTemporarySourceTest {
    @Test void nativeBodyCombinesWithOriginalAckPermissionOccupancyAndNextStop() throws Exception {
        var f = new RecoveryNativeTemporaryFixture();

        var proof = f.read();

        assertThat(proof.identity()).isEqualTo(f.initial.expected());
        assertThat(proof.auditEventCount()).isEqualTo(5); assertThat(proof.retainedRowCount()).isEqualTo(1);
        assertThat(proof.payloadSha256()).isEqualTo(SubmissionJson.sha(f.evidence.frozen));
        assertThat(f.evidence.identity.auditThroughSeq()).isEqualTo(6);
        assertThat(f.initial.state.payload.has("auditEvents")).isFalse();
        assertThat(proof.ordinaryWritesAllowed()).isFalse(); assertThat(proof.localDeletionAllowed()).isFalse();
    }

    @Test void anotherSignedSourceCannotBeCombinedWithPinnedOriginals() throws Exception {
        var f = new RecoveryNativeTemporaryFixture(); var other = new RecoveryOriginalEvidenceFixture();

        assertThatThrownBy(() -> f.initial.reader().readTemporary(f.initial.request(), other.request()))
            .hasMessage("RECOVERY_INITIAL_NATIVE_IDENTITY");
    }

    @Test void sameIdentityWithDifferentOriginalBodyMustBeRejected() throws Exception {
        var f = new RecoveryNativeTemporaryFixture(); f.evidence.frozen.put("temporaryNo", "changed original");
        f.evidence.replace("STOP", f.evidence.frozen); f.evidence.rebuild();

        assertThatThrownBy(f::read).hasMessage("RECOVERY_INITIAL_NATIVE_PAYLOAD");
    }

    @Test void independentlyPinnedFrozenWatermarkCannotAdoptTheStopWatermark() throws Exception {
        var f = new RecoveryNativeTemporaryFixture();
        ((ObjectNode)f.initial.state.authority.source.manifest.path("source")).put("auditThroughSeq", "6"); f.initial.publish();

        assertThatThrownBy(f::read).hasMessage("RECOVERY_INITIAL_NATIVE_IDENTITY");
    }

    @Test void anotherHistoricalDeviceKeyCannotProduceNativeCombination() throws Exception {
        var f = new RecoveryNativeTemporaryFixture(); var other = new RecoveryOriginalEvidenceFixture();

        assertThatThrownBy(() -> f.initial.reader().readTemporary(f.initial.request(), f.evidence.request(other.key.getPublic(), f.evidence.identity)))
            .hasMessage("RECOVERY_E2A_INVALID");
    }

    @Test void originalActivationIsStillRequiredBeforeAnyNativeCombination() throws Exception {
        var f = new RecoveryNativeTemporaryFixture(); f.initial.prep.origin.remove("activation");
        f.initial.prep.write(); f.initial.publish();

        assertThatThrownBy(f::read).hasMessage("RECOVERY_TEMP_ACTIVATION_REQUIRED");
    }

    @Test void nativeEvidenceCannotOverrideAnOriginalSuccessTerminal() throws Exception {
        var f = new RecoveryNativeTemporaryFixture(); var source = f.initial.state.authority.source;
        var receipt = f.initial.state.authority.key.receipt(source, "SUCCEEDED")
            .put("payloadSha256", SubmissionJson.sha(f.initial.state.payload)).put("captureHighWatermark", "1");
        f.initial.state.object("result", f.initial.state.authority.key.signed(receipt));

        assertThatThrownBy(f::read).hasMessage("RECOVERY_INITIAL_TERMINAL");
    }

    @Test void nativeConsumerRefusesAnArchivePrefix() throws Exception {
        var f = new RecoveryNativeTemporaryFixture();
        var prefix = new RecoveryAuditArchiveProof.Segment(SubmissionJson.object(), new byte[]{1});
        var request = new RecoveryInitialSource.Request(f.initial.request().materialRoot(),
            f.initial.request().independentManifestSha256(), f.initial.expected(), List.of(prefix));

        assertThatThrownBy(() -> f.initial.reader().readTemporary(request, f.evidence.request()))
            .hasMessage("RECOVERY_INITIAL_NATIVE_REQUEST");
    }

    @Test void formalSourceCannotEnterTheNativeTemporaryConsumer() throws Exception {
        var f = new RecoveryNativeTemporaryFixture(); var formal = new RecoveryInitialFixture("FORMAL");

        assertThatThrownBy(() -> formal.reader().readTemporary(formal.request(), f.evidence.request()))
            .hasMessage("RECOVERY_INITIAL_NATIVE_REQUEST");
    }

    @Test void laterRoundCannotEnterTheFirstRoundNativeConsumer() throws Exception {
        var f = new RecoveryNativeTemporaryFixture(); var id = f.initial.expected();
        var later = new RecoverySourceIdentity(id.sourceKind(), id.warehouseId(), id.scopeId(), id.submissionId(), id.deviceId(),
            id.operatorId(), id.qualificationId(), null, id.creatorDeviceId(), id.clientOrderId(), id.sourceRecoveryEpoch(),
            2, id.captureHighWatermark(), id.auditThroughSeq(), id.orderScanHighWatermark());
        var request = new RecoveryInitialSource.Request(f.initial.request().materialRoot(),
            f.initial.request().independentManifestSha256(), later, List.of());

        assertThatThrownBy(() -> f.initial.reader().readTemporary(request, f.evidence.request())).hasMessage("RECOVERY_INITIAL_ROUND");
    }

    @Test void nativeEvidenceCannotOverrideRevokedHistoricalPermission() throws Exception {
        var f = new RecoveryNativeTemporaryFixture(); f.initial.state.firstWindow().put("userActive", false); f.initial.publish();

        assertThatThrownBy(f::read).hasMessage("RECOVERY_SOURCE_PERMISSION_REVOKED");
    }

    @Test void nativeEvidenceCannotOverrideCompetingPhysicalOwnership() throws Exception {
        var f = new RecoveryNativeTemporaryFixture(); f.initial.state.row().put("pcWorkId", UUID.randomUUID().toString()); f.initial.publish();

        assertThatThrownBy(f::read).hasMessage("RECOVERY_SOURCE_OCCUPANCY_CHAIN_UNSUPPORTED");
    }

    @ParameterizedTest @ValueSource(strings = {"origin", "payload", "result", "permission", "occupancy"})
    void laterNativeReadRechecksAllPinnedOriginals(String kind) throws Exception {
        var f = new RecoveryNativeTemporaryFixture(); f.read(); var file = f.initial.state.authority.source.object(kind);
        byte[] original = Files.readAllBytes(file); original[0] ^= 1; Files.write(file, original);

        assertThatThrownBy(f::read).hasMessage("RECOVERY_SOURCE_OBJECT_MISMATCH");
    }
}
