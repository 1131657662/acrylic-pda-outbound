package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceEvidenceIntakeOrderScanSequenceTest {
    private static final UUID DEVICE = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");

    @Test void derivesTheTrustedHighWatermarkFromContinuousSourceSequences() {
        var first = capture(1);
        var second = capture(2);
        var payload = payload(first, second, scan(first), scan(second));

        assertThat(SourceEvidenceIntakeOrderScanSequence.require(payload, DEVICE)).isEqualTo(2);
    }

    @Test void rejectsASequenceGapInsteadOfUsingCaptureCount() {
        var first = capture(1);
        var second = capture(3);

        assertCode("E2A_ORDER_SCAN_SEQUENCE_CONFLICT",
            () -> SourceEvidenceIntakeOrderScanSequence.require(payload(first, second), DEVICE));
    }

    @Test void rejectsASequenceFromAnotherDevice() {
        var foreign = capture(1).put("originDeviceId", UUID.randomUUID().toString());

        assertCode("E2A_ORDER_SCAN_SOURCE_CONFLICT",
            () -> SourceEvidenceIntakeOrderScanSequence.require(payload(foreign), DEVICE));
    }

    @Test void rejectsAScanWhoseSequenceDiffersFromItsCapture() {
        var capture = capture(1);
        var scan = scan(capture).put("orderScanSeq", "2");

        assertCode("E2A_ORDER_SCAN_SEQUENCE_CONFLICT",
            () -> SourceEvidenceIntakeOrderScanSequence.require(payload(capture, scan), DEVICE));
    }

    @Test void rejectsMissingOrderSequence() {
        var capture = capture(1);
        capture.remove("orderScanSeq");

        assertThatThrownBy(() -> SourceEvidenceIntakeOrderScanSequence.require(payload(capture), DEVICE))
            .isInstanceOf(BusinessException.class);
    }

    @Test void receivingDeviceContinuesAfterVerifiedForeignPrefixWithoutRenumberingDevices() {
        var original = capture(1).put("originDeviceId", UUID.randomUUID().toString()).put("deviceCaptureSeq", "1");
        var current = capture(2).put("deviceCaptureSeq", "1");
        var prefix = payload(original, scan(original));
        var cumulative = payload(original, current, scan(original), scan(current));

        assertThat(SourceEvidenceIntakeOrderScanSequence.require(cumulative, DEVICE, prefix)).isEqualTo(2);
        assertThat(current.path("deviceCaptureSeq").asText()).isEqualTo("1");
    }

    @Test void rejectsForeignCaptureOutsideTheVerifiedPrefix() {
        var original = capture(1).put("originDeviceId", UUID.randomUUID().toString());
        var foreign = capture(2).put("originDeviceId", original.path("originDeviceId").asText());

        assertCode("E2A_ORDER_SCAN_SOURCE_CONFLICT", () -> SourceEvidenceIntakeOrderScanSequence.require(
            payload(original, foreign), DEVICE, payload(original)));
    }

    @Test void rejectsRewrittenPrefixEvenWhenSequenceAndDeviceRemainPlausible() {
        var original = capture(1).put("deviceCaptureSeq", "23");
        var rewritten = original.deepCopy().put("deviceCaptureSeq", "24");

        assertCode("E2A_ORDER_SCAN_PREFIX_CONFLICT", () -> SourceEvidenceIntakeOrderScanSequence.require(
            payload(rewritten, capture(2)), DEVICE, payload(original)));
    }

    @Test void rejectsReceivingSequenceRestartInsteadOfRenumberingTheCumulativeOrder() {
        var original = capture(1);

        assertCode("E2A_ORDER_SCAN_SEQUENCE_CONFLICT", () -> SourceEvidenceIntakeOrderScanSequence.require(
            payload(original, capture(1)), DEVICE, payload(original)));
    }

    @Test void rejectsDuplicatedCaptureIdAcrossThePrefixBoundary() {
        var original = capture(1);
        var duplicate = capture(2).put("id", original.path("id").asText());

        assertCode("E2A_ORDER_SCAN_SEQUENCE_CONFLICT", () -> SourceEvidenceIntakeOrderScanSequence.require(
            payload(original, duplicate), DEVICE, payload(original)));
    }

    @Test void rejectsOmittedOriginalScan() {
        var original = capture(1);

        assertCode("E2A_ORDER_SCAN_PREFIX_CONFLICT", () -> SourceEvidenceIntakeOrderScanSequence.require(
            payload(original, capture(2)), DEVICE, payload(original, scan(original))));
    }

    private static ObjectNode payload(ObjectNode... values) {
        var payload = SubmissionJson.object();
        var captures = payload.putArray("captures");
        var scans = payload.putArray("scans");
        payload.putArray("events");
        for (var value : values) {
            if (value.has("captureId")) scans.add(value); else captures.add(value);
        }
        return payload;
    }

    private static ObjectNode capture(long sequence) {
        return SubmissionJson.object().put("id", UUID.randomUUID().toString())
            .put("originDeviceId", DEVICE.toString()).put("orderScanSeq", Long.toString(sequence));
    }

    private static ObjectNode scan(ObjectNode capture) {
        return SubmissionJson.object().put("captureId", capture.path("id").asText())
            .put("orderScanSeq", capture.path("orderScanSeq").asText());
    }

    private static void assertCode(String code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo(code));
    }
}
