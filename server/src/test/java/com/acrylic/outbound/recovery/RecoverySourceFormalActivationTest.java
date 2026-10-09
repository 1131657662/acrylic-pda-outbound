package com.acrylic.outbound.recovery;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoverySourceFormalActivationTest {
    @Test void provesPinnedUnsignedNativeAckWithoutGrantingWork() throws Exception {
        var f = new RecoveryFormalActivationFixture();

        var proof = f.reader().verify(f.prep.read());

        assertThat(proof.activatedAt()).isEqualTo(Instant.parse("2026-09-20T00:02:00.123456789Z"));
        assertThat(proof.operationEventId()).isEqualTo("1");
        assertThat(f.prep.read().ordinaryWritesAllowed()).isFalse(); assertThat(f.prep.read().localDeletionAllowed()).isFalse();
    }

    @Test void refusesHttpFactsWithoutAnIndependentPin() throws Exception {
        var f = new RecoveryFormalActivationFixture(); var source = f.prep.read();
        var unpinned = new RecoveryPinnedSource(source.identity(), null, null,
            Map.of("origin", source.original("origin"), "payload", source.original("payload")));

        assertThatThrownBy(() -> f.reader().verify(unpinned)).hasMessage("RECOVERY_FORMAL_ACTIVATION_PIN_REQUIRED");
    }

    @Test void refusesMissingOriginalAck() throws Exception {
        var f = new RecoveryFormalActivationFixture(); f.prep.origin.remove("activation"); f.prep.write();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_FORMAL_ACTIVATION_REQUIRED");
    }

    @ParameterizedTest @ValueSource(strings = {"id", "orderId", "warehouseId", "deviceId", "operatorId"})
    void refusesForeignAssignment(String field) throws Exception {
        var f = new RecoveryFormalActivationFixture(); f.assignment.put(field, UUID.randomUUID().toString()); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_FORMAL_ACTIVATION_ASSIGNMENT");
    }

    @ParameterizedTest @ValueSource(strings = {"bundleId", "inventorySnapshotId"})
    void refusesForeignCommand(String field) throws Exception {
        var f = new RecoveryFormalActivationFixture(); f.command.put(field, UUID.randomUUID().toString()); f.bindCommand(); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_FORMAL_ACTIVATION_COMMAND");
    }

    @ParameterizedTest @ValueSource(strings = {"actorId", "entityId"})
    void refusesForeignOriginalAudit(String field) throws Exception {
        var f = new RecoveryFormalActivationFixture(); f.event.put(field, UUID.randomUUID().toString()); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_FORMAL_ACTIVATION_EVENT");
    }

    @Test void refusesAnotherEventType() throws Exception {
        var f = new RecoveryFormalActivationFixture(); f.event.put("eventType", "ORDER_ASSIGN"); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_FORMAL_ACTIVATION_EVENT");
    }

    @Test void refusesChangedOriginalAuditCommand() throws Exception {
        var f = new RecoveryFormalActivationFixture(); f.command.put("activatedAt", "2026-09-20T00:02:00.123999999Z"); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_FORMAL_ACTIVATION_EVENT_COMMAND");
    }

    @Test void refusesMissingOriginalAudit() throws Exception {
        var f = new RecoveryFormalActivationFixture(); f.event.remove("id"); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("CONTROL_FIELDS");
    }

    @Test void refusesRenewedAssignmentInTheInitialProof() throws Exception {
        var f = new RecoveryFormalActivationFixture(); f.assignment.put("authorizationSequence", "1"); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_FORMAL_ACTIVATION_BASELINE");
    }

    @Test void refusesForeignReceipt() throws Exception {
        var f = new RecoveryFormalActivationFixture(); f.receipt.put("assignmentId", UUID.randomUUID().toString()); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_FORMAL_ACTIVATION_RECEIPT");
    }

    @Test void refusesReceiptDisagreeingWithStoredOriginalAckTime() throws Exception {
        var f = new RecoveryFormalActivationFixture(); f.write();
        ((com.fasterxml.jackson.databind.node.ObjectNode)f.prep.origin.at("/activation/assignment"))
            .put("downloadAckAt", "2026-09-20T00:04:00Z"); f.prep.write();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_FORMAL_ACTIVATION_RECEIPT");
    }

    @ParameterizedTest @ValueSource(strings = {"2026-09-20T00:04:00Z", "2026-09-20T00:03:00.123456Z"})
    void refusesAuditAfterAcknowledgementOrNonNativePrecision(String time) throws Exception {
        var f = new RecoveryFormalActivationFixture(); f.event.put("serverTime", time); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_FORMAL_ACTIVATION_TIME");
    }

    @Test void refusesAcknowledgementAtOriginalExpiry() throws Exception {
        var f = new RecoveryFormalActivationFixture(); f.receipt.put("downloadAckAt", "2026-09-21T00:00:00Z"); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_FORMAL_ACTIVATION_WINDOW");
    }

    @Test void refusesActivationAfterOriginalConfirmation() throws Exception {
        var f = new RecoveryFormalActivationFixture(); f.command.put("activatedAt", "2026-09-20T00:30:01Z");
        f.event.put("serverTime", "2026-09-20T00:30:02Z"); f.receipt.put("downloadAckAt", "2026-09-20T00:30:03Z");
        f.bindCommand(); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_FORMAL_ACTIVATION_SAVE");
    }

    @Test void refusesActivationBeyondNativeClockTolerance() throws Exception {
        var f = new RecoveryFormalActivationFixture(); f.command.put("activatedAt", "2026-09-20T00:08:00.124Z"); f.bindCommand(); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_FORMAL_ACTIVATION_TIME");
    }

    @Test void acceptsNativeClockToleranceBeforeTheTruncatedMillisecondUpperBound() throws Exception {
        var f = new RecoveryFormalActivationFixture(); f.command.put("activatedAt", "2026-09-20T00:08:00.123999999Z");
        f.bindCommand(); f.write();

        assertThat(f.reader().verify(f.prep.read()).activatedAt()).isEqualTo(Instant.parse("2026-09-20T00:08:00.123999999Z"));
    }

    @Test void acceptsDelayedOriginalAckWithoutChangingEarlierSaveAuthority() throws Exception {
        var f = new RecoveryFormalActivationFixture(); f.event.put("serverTime", "2026-09-20T00:40:00Z");
        f.receipt.put("downloadAckAt", "2026-09-20T00:40:01Z"); f.write();

        assertThat(f.reader().verify(f.prep.read()).acknowledgedAt()).isEqualTo(Instant.parse("2026-09-20T00:40:01Z"));
    }

    @ParameterizedTest @ValueSource(strings = {"epoch", "authorizationSequence", "serverRecoveryEpoch"})
    void refusesForeignEpochOrOriginalSequence(String field) throws Exception {
        var f = new RecoveryFormalActivationFixture(); f.assignment.put(field, "2"); f.write();
        String code = field.equals("serverRecoveryEpoch") ? "RECOVERY_FORMAL_ACTIVATION_ASSIGNMENT" : "RECOVERY_FORMAL_ACTIVATION_BASELINE";

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage(code);
    }

    @Test void refusesForeignOriginalAuthorization() throws Exception {
        var f = new RecoveryFormalActivationFixture(); f.assignment.put("offlineAuthorizationId", UUID.randomUUID().toString()); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_FORMAL_ACTIVATION_BASELINE");
    }

    @Test void refusesRequestIdBeyondTheNativeAuditColumn() throws Exception {
        var f = new RecoveryFormalActivationFixture(); f.event.put("requestId", "x".repeat(65)); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_FORMAL_ACTIVATION_EVENT");
    }
}
