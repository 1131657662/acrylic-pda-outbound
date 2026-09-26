package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class UnsubmittedSourceRecordsTest {
    private final UnsubmittedSourceRecordFixture f = new UnsubmittedSourceRecordFixture();

    @Test void expiredOriginalAuthorityStillExplainsOriginalSavedScan() {
        assertThatCode(f::validate).doesNotThrowAnyException();
    }

    @ParameterizedTest @ValueSource(strings = {"RECEIVED", "WAITING_CONFIRM", "CONFIRM_REQUESTED"})
    void pendingInputRemainsPendingWithoutInventedScan(String state) {
        int count = List.of("RECEIVED", "WAITING_CONFIRM", "CONFIRM_REQUESTED").indexOf(state) + 1;
        f.capture.put("state", state);
        if (!state.equals("CONFIRM_REQUESTED")) f.capture.putNull("confirmedAt");
        assertThatCode(() -> UnsubmittedSourceRecords.validate(f.capture, null, f.events.subList(0, count), f.origin))
            .doesNotThrowAnyException();
    }

    @Test void savedCaptureCannotOmitItsScan() {
        assertThatThrownBy(() -> UnsubmittedSourceRecords.validate(f.capture, null, f.events, f.origin)).isInstanceOf(BusinessException.class);
    }

    @Test void voidRequiresOriginalLaterVoidEvent() {
        f.scan.put("status", "VOID");
        assertThatThrownBy(f::validate).isInstanceOf(BusinessException.class);
        f.event("VOID");
        assertThatCode(f::validate).doesNotThrowAnyException();
    }

    @Test void activeCannotHideOriginalVoid() {
        f.event("VOID");
        assertThatThrownBy(f::validate).isInstanceOf(BusinessException.class);
    }

    @Test void sameEventsInWrongOrderAreRejected() {
        java.util.Collections.swap(f.events, 1, 2);
        assertThatThrownBy(f::validate).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"captureId", "originDeviceId", "operatorId", "authorizationId"})
    void changedEventOriginIsRejected(String field) {
        ((ObjectNode) f.events.get(2)).put(field, java.util.UUID.randomUUID().toString());
        assertThatThrownBy(f::validate).isInstanceOf(BusinessException.class);
    }

    @Test void mismatchedClientSourceIsRejected() {
        ((ObjectNode) f.events.get(2).path("source")).put("clientOrderId", java.util.UUID.randomUUID().toString());
        assertThatThrownBy(f::validate).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"2026-09-23T23:59:59Z", "2026-09-24T02:00:00Z"})
    void confirmationOutsideOriginalValidityIsRejected(String time) {
        f.capture.put("confirmedAt", time);
        assertThatThrownBy(f::validate).isInstanceOf(BusinessException.class);
    }

    @Test void newerCycleCannotReplaceOriginal() {
        f.capture.put("expectedDispatchCycle", "3"); f.scan.put("expectedDispatchCycle", "3");
        assertThatThrownBy(f::validate).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"model", "name", "deliveryCode", "rawWeight", "weightGrams", "baleId"})
    void suppliedScanCannotReplaceOriginalStock(String field) {
        f.scan.put(field, field.equals("baleId") ? java.util.UUID.randomUUID().toString() : "2");
        assertThatThrownBy(f::validate).isInstanceOf(BusinessException.class);
    }

    @Test void missingConfirmationCannotBeRepairedBySavedEvent() {
        f.events.remove(2);
        assertThatThrownBy(f::validate).isInstanceOf(BusinessException.class);
    }

    @Test void manualConfirmationCannotSkipValidatedWaitingState() {
        f.events.remove(1);
        assertThatThrownBy(f::validate).isInstanceOf(BusinessException.class);
    }

    @Test void broadcastConfirmationCannotInventManualWaitingState() {
        f.capture.put("inputSource", "BROADCAST").put("confirmationPolicy", "AUTO_AFTER_VALIDATION");
        assertThatThrownBy(f::validate).isInstanceOf(BusinessException.class);
        f.events.remove(1);
        assertThatCode(f::validate).doesNotThrowAnyException();
    }

    @Test void duplicateSavedEventIsRejected() {
        f.event("SAVED");
        assertThatThrownBy(f::validate).isInstanceOf(BusinessException.class);
    }

    @Test void discardedManualInputRequiresNoConfirmation() {
        f.capture.put("state", "DISCARDED").putNull("confirmedAt");
        f.events.subList(2, 4).clear(); f.event("DISCARDED");
        assertThatCode(() -> UnsubmittedSourceRecords.validate(f.capture, null, f.events, f.origin)).doesNotThrowAnyException();
    }

    @Test void failureAcknowledgementIsRetainedWithoutInventingScan() {
        f.capture.put("state", "INVALID").putNull("confirmedAt").put("failureAckAt", "2026-09-24T01:00:02Z");
        f.events.subList(1, 4).clear(); f.event("INVALID"); f.event("FAILURE_ACKNOWLEDGED");
        assertThatCode(() -> UnsubmittedSourceRecords.validate(f.capture, null, f.events, f.origin)).doesNotThrowAnyException();
    }
}
