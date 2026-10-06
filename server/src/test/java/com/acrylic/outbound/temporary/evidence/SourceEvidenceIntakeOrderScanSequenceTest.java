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

    private static ObjectNode payload(ObjectNode... values) {
        var payload = SubmissionJson.object();
        var captures = payload.putArray("captures");
        var scans = payload.putArray("scans");
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
