package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryInitialSourceTest {
    @ParameterizedTest @ValueSource(strings = {"FORMAL", "TEMP"})
    void combinesSamePinnedSourceWithDistinctDeviceAndOrderWatermarks(String kind) throws Exception {
        var f = new RecoveryInitialFixture(kind);

        var proof = f.reader().read(f.request());

        assertThat(proof.identity()).isEqualTo(f.expected());
        assertThat(proof.identity().captureHighWatermark()).isEqualTo(7);
        assertThat(proof.identity().orderScanHighWatermark()).isEqualTo(1);
        assertThat(proof.auditEventCount()).isEqualTo(5); assertThat(proof.retainedRowCount()).isEqualTo(1);
        assertThat(proof.originalSha256()).hasSize(5);
        assertThat(proof.ordinaryWritesAllowed()).isFalse(); assertThat(proof.localDeletionAllowed()).isFalse();
        assertThatThrownBy(() -> proof.originalSha256().put("payload", "changed")).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"FORMAL", "TEMP"})
    void combinesRetainedSignedAuditPrefixWithOriginalTail(String kind) throws Exception {
        var f = new RecoveryInitialFixture(kind); f.archivePrefix();

        assertThat(f.reader().read(f.request()).auditEventCount()).isEqualTo(5);
    }

    @ParameterizedTest @ValueSource(strings = {"origin", "payload", "result", "occupancy", "permission"})
    void rechecksEveryOriginalAfterEarlierSuccessfulCombination(String kind) throws Exception {
        var f = new RecoveryInitialFixture("FORMAL"); var reader = f.reader(); reader.read(f.request());
        var path = f.state.authority.source.object(kind); byte[] bytes = Files.readAllBytes(path); bytes[0] ^= 1; Files.write(path, bytes);

        assertThatThrownBy(() -> reader.read(f.request())).hasMessage("RECOVERY_SOURCE_OBJECT_MISMATCH");
    }

    @ParameterizedTest @ValueSource(strings = {"captureHighWatermark", "orderScanHighWatermark"})
    void refusesIndependentlyPinnedWatermarkDifferentFromCompleteBody(String field) throws Exception {
        var f = new RecoveryInitialFixture("FORMAL");
        ((ObjectNode)f.state.authority.source.manifest.path("source")).put(field, "8"); f.publish();

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_INITIAL_WATERMARK");
    }

    @Test void refusesAnotherSubmissionEvenWhenReceiptAndAuditAreSeparatelyValid() throws Exception {
        var f = new RecoveryInitialFixture("FORMAL"); UUID other = UUID.randomUUID();
        f.state.payload.put("submissionId", other.toString());
        ((ObjectNode)f.state.payload.at("/events/4")).put("detail", other.toString());
        ((ObjectNode)f.state.payload.at("/auditEvents/4")).put("detail", other.toString()); f.publish();
        new RecoverySourceAudit(f.keys()).verify(f.source().original("payload"),
            new RecoverySourceAudit.Context(f.expected(), Map.of(f.expected().qualificationId(), f.expected()), List.of()));
        new RecoverySourceTerminal(new RecoveryReceiptVerifier(f.keys())).verify(f.source());

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_INITIAL_SUBMISSION");
    }

    @Test void refusesPermissionFromAnotherOriginalEvenWithBothDescriptorsPinned() throws Exception {
        var f = new RecoveryInitialFixture("FORMAL"); var other = new RecoveryInitialFixture("FORMAL");
        f.state.authority.key.object(f.state.authority.source, "permission", other.source().original("permission")); f.state.authority.source.publish();

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_SOURCE_PERMISSION_SOURCE");
    }

    @Test void refusesMissingAckDespiteAllOtherReadableOriginals() throws Exception {
        var f = new RecoveryInitialFixture("FORMAL"); f.prep.origin.remove("activation"); f.prep.write(); f.publish();

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_FORMAL_ACTIVATION_REQUIRED");
    }

    @Test void refusesPermissionRevokedAtOriginalSave() throws Exception {
        var f = new RecoveryInitialFixture("TEMP"); f.state.firstWindow().put("userActive", false); f.publish();

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_SOURCE_PERMISSION_REVOKED");
    }

    @Test void refusesCompetingOriginalStockOwner() throws Exception {
        var f = new RecoveryInitialFixture("FORMAL"); f.state.row().put("continuationId", UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_CHAIN_UNSUPPORTED");
    }

    @Test void refusesSuccessInReservationRecovery() throws Exception {
        var f = new RecoveryInitialFixture("FORMAL"); f.state.status = "SUCCEEDED"; f.publish();

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_INITIAL_TERMINAL");
    }

    @Test void refusesStaleControlAfterEarlierSuccessfulCombination() throws Exception {
        var f = new RecoveryInitialFixture("FORMAL"); var reader = f.reader(); reader.read(f.request());
        f.state.authority.source.control.append(f.state.authority.source.control.next("VERIFYING").put("epochId", UUID.randomUUID().toString()));

        assertThatThrownBy(() -> reader.read(f.request())).hasMessage("CONTROL_EPOCH_TRANSITION");
    }

    @Test void retainsLocallyFrozenPayloadOnlyWithOriginalReceiptDigest() throws Exception {
        var f = new RecoveryInitialFixture("TEMP"); var source = f.state.authority.source;
        String hash = com.acrylic.outbound.submissions.SubmissionJson.sha(f.state.payload);
        var receipt = f.state.authority.key.receipt(source, "ABORTED").putNull("payloadSha256")
            .put("payloadPresent", false).put("localFrozenPayloadSha256", hash);
        f.state.object("result", f.state.authority.key.signed(receipt));

        assertThat(f.reader().read(f.request()).payloadSha256()).isEqualTo(hash);
    }

    @Test void refusesLocalBodyWithoutOriginalReceiptDigest() throws Exception {
        var f = new RecoveryInitialFixture("FORMAL"); var source = f.state.authority.source;
        var receipt = f.state.authority.key.receipt(source, "ABORTED").putNull("payloadSha256").put("payloadPresent", false);
        f.state.object("result", f.state.authority.key.signed(receipt));

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_SOURCE_OCCUPANCY_TERMINAL_PAYLOAD");
    }

    @Test void refusesUnknownPublicKeyWithoutLoadingAnyPrivateKey() throws Exception {
        var f = new RecoveryInitialFixture("FORMAL"); var other = new RecoveryReceiptFixture();
        var reader = new RecoveryInitialSource(f.state.authority.source.reader(),
            new RecoveryReceiptKeys(List.of(other.pair.getPublic().getEncoded())));

        assertThatThrownBy(() -> reader.read(f.request())).hasMessage("RECOVERY_RECEIPT_KEY_UNKNOWN");
    }
}
