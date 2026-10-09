package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryInitialRecordsTest {
    @Test void refusesMissingCaptureEventsEvenWhenContinuousAuditSeparatelyPasses() throws Exception {
        var f = new RecoveryInitialFixture("FORMAL");
        for (int index = 0; index < 4; index++) {
            ((ObjectNode)f.state.payload.path("events").get(index)).putNull("captureId");
            ((ObjectNode)f.state.payload.path("auditEvents").get(index)).putNull("captureId");
        }
        f.publish(); new RecoverySourceAudit(f.keys()).verify(f.source().original("payload"),
            new RecoverySourceAudit.Context(f.expected(), Map.of(f.expected().qualificationId(), f.expected()), List.of()));

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_INITIAL_AUDIT_STATE");
    }

    @Test void refusesSavedWithoutConfirmIntentEvenWithAllAuditSequencesPresent() throws Exception {
        var f = new RecoveryInitialFixture("TEMP"); f.eventType(2, "WAITING_CONFIRM"); f.publish();

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_INITIAL_AUDIT_TRANSITION");
    }

    @Test void refusesForgedCaptureReferenceInContinuousAudit() throws Exception {
        var f = new RecoveryInitialFixture("FORMAL"); String other = UUID.randomUUID().toString();
        ((ObjectNode)f.state.payload.at("/events/0")).put("captureId", other);
        ((ObjectNode)f.state.payload.at("/auditEvents/0")).put("captureId", other); f.publish();

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_INITIAL_AUDIT_CAPTURE");
    }

    @Test void refusesScanReferencingAnotherEventInsteadOfItsOriginalSave() throws Exception {
        var f = new RecoveryInitialFixture("TEMP"); f.scan().put("localSeq", "3"); f.publish();

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_INITIAL_SAVE_EVENT");
    }

    @Test void refusesVoidWithoutItsOriginalPhysicalAudit() throws Exception {
        var f = new RecoveryInitialFixture("FORMAL"); f.scan().put("status", "VOID");
        f.state.payload.put("expectedCount", "0").put("expectedWeightGrams", "0").put("shortShipmentReason", "original physical check"); f.publish();

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_INITIAL_VOID_EVENT");
    }

    @Test void refusesChangedRawInputDespiteValidPreparedBarcodeAndStock() throws Exception {
        var f = new RecoveryInitialFixture("FORMAL"); f.capture().put("rawInput", "01234567891"); f.publish();

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_INITIAL_RAW_INPUT");
    }

    @Test void refusesReorderedBodyEventsDespiteMatchingAuditSet() throws Exception {
        var f = new RecoveryInitialFixture("FORMAL"); var events = (ArrayNode)f.state.payload.path("events");
        var first = events.remove(0); events.insert(1, first); f.publish();

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_INITIAL_AUDIT_ORDER");
    }

    @Test void refusesGapInOriginalOrderInputs() throws Exception {
        var f = new RecoveryInitialFixture("FORMAL"); f.capture().put("orderScanSeq", "2"); f.scan().put("orderScanSeq", "2"); f.publish();

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_INITIAL_ORDER_SEQUENCE");
    }

    @ParameterizedTest @ValueSource(strings = {"received", "saved", "voided", "pending", "invalid", "businessRejected", "discarded"})
    void refusesInconsistentTerminalSummary(String field) throws Exception {
        var f = new RecoveryInitialFixture("FORMAL"); ((ObjectNode)f.state.payload.path("captureSummary")).put(field, "9"); f.publish();

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_INITIAL_SUMMARY");
    }

    @Test void acceptsBroadcastOriginalWithoutManualWaiting() throws Exception {
        var f = new RecoveryInitialFixture("TEMP"); f.capture().put("inputSource", "BROADCAST").put("confirmationPolicy", "AUTO_AFTER_VALIDATION");
        for (String array : List.of("events", "auditEvents")) {
            var events = (ArrayNode)f.state.payload.path(array); events.remove(1);
            for (int index = 0; index < events.size(); index++) ((ObjectNode)events.get(index)).put("localSeq", Integer.toString(index + 1));
        }
        f.scan().put("localSeq", "3"); ((ObjectNode)f.state.payload.at("/auditCoverage/0")).put("throughSeq", "4");
        ((ObjectNode)f.state.authority.source.manifest.path("source")).put("auditThroughSeq", "4"); f.publish();

        assertThat(f.reader().read(f.request()).auditEventCount()).isEqualTo(4);
    }

    @ParameterizedTest @ValueSource(strings = {"INVALID", "BUSINESS_REJECTED", "DISCARDED"})
    void retainsOriginalNonSavedTerminalCapture(String terminal) throws Exception {
        var f = new RecoveryInitialFixture("FORMAL"); f.terminalCapture(terminal);

        assertThat(f.reader().read(f.request()).retainedRowCount()).isZero();
    }

    @Test void retainsVoidScanWithOriginalPhysicalAuditAndNoReservation() throws Exception {
        var f = new RecoveryInitialFixture("TEMP"); f.scan().put("status", "VOID");
        f.state.payload.put("expectedCount", "0").put("expectedWeightGrams", "0").put("shortShipmentReason", "original physical check");
        ((ObjectNode)f.state.payload.path("captureSummary")).put("saved", "0").put("voided", "1");
        ((ArrayNode)f.state.occupancy.path("rows")).removeAll();
        var events = (ArrayNode)f.state.payload.path("events"); var voided = ((ObjectNode)events.get(3)).deepCopy();
        voided.put("id", UUID.randomUUID().toString()).put("type", "VOID").put("localSeq", "5");
        events.insert(4, voided); ((ObjectNode)events.get(5)).put("localSeq", "6");
        f.state.payload.set("auditEvents", events.deepCopy());
        ((ObjectNode)f.state.payload.at("/auditCoverage/0")).put("throughSeq", "6");
        ((ObjectNode)f.state.authority.source.manifest.path("source")).put("auditThroughSeq", "6"); f.publish();

        assertThat(f.reader().read(f.request()).retainedRowCount()).isZero();
    }

    @Test void refusesFailureAcknowledgementForAnotherReason() throws Exception {
        var f = new RecoveryInitialFixture("TEMP"); f.terminalCapture("INVALID");
        ((ObjectNode)f.state.payload.at("/events/2")).put("detail", "different reason");
        ((ObjectNode)f.state.payload.at("/auditEvents/2")).put("detail", "different reason"); f.publish();

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_INITIAL_FAILURE_DETAIL");
    }

    @Test void refusesMissingFailureAcknowledgement() throws Exception {
        var f = new RecoveryInitialFixture("TEMP"); f.terminalCapture("INVALID");
        for (String array : List.of("events", "auditEvents")) {
            var events = (ArrayNode)f.state.payload.path(array); events.remove(2); ((ObjectNode)events.get(2)).put("localSeq", "3");
        }
        f.capture().putNull("failureAckAt"); ((ObjectNode)f.state.payload.at("/auditCoverage/0")).put("throughSeq", "3");
        ((ObjectNode)f.state.authority.source.manifest.path("source")).put("auditThroughSeq", "3"); f.publish();

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_INITIAL_FAILURE_ACK");
    }

    @Test void refusesZeroDeviceSequenceEvenWithPinnedZeroWatermark() throws Exception {
        var f = new RecoveryInitialFixture("FORMAL"); f.capture().put("deviceCaptureSeq", "0");
        ((ObjectNode)f.state.authority.source.manifest.path("source")).put("captureHighWatermark", "0"); f.publish();

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_INITIAL_DEVICE_SEQUENCE");
    }

    @Test void refusesUnexpectedFormalSourceOrder() throws Exception {
        var f = new RecoveryInitialFixture("FORMAL"); ((ObjectNode)f.state.payload.path("source")).put("sourceOrderId", UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_INITIAL_SOURCE");
    }

    @Test void refusesBroadcastAuditContainingManualWaiting() throws Exception {
        var f = new RecoveryInitialFixture("FORMAL"); f.capture().put("inputSource", "BROADCAST").put("confirmationPolicy", "AUTO_AFTER_VALIDATION"); f.publish();

        assertThatThrownBy(() -> f.reader().read(f.request())).hasMessage("RECOVERY_INITIAL_CONFIRM_EVENT");
    }
}
