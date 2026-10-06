package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.UUID;

/** Derives the trusted TEMP order sequence from the verified source payload. */
final class SourceEvidenceIntakeOrderScanSequence {
    private SourceEvidenceIntakeOrderScanSequence() { }

    static long require(JsonNode payload, UUID creatorDeviceId) {
        var sequences = new HashMap<UUID, Long>();
        long expected = 1;
        for (var capture : SubmissionJson.array(payload, "captures")) {
            require(SubmissionJson.uuid(capture, "originDeviceId").equals(creatorDeviceId),
                "E2A_ORDER_SCAN_SOURCE_CONFLICT");
            long sequence = SubmissionJson.number(capture, "orderScanSeq");
            require(sequence == expected, "E2A_ORDER_SCAN_SEQUENCE_CONFLICT");
            sequences.put(SubmissionJson.uuid(capture, "id"), sequence);
            try {
                expected = Math.addExact(expected, 1);
            } catch (ArithmeticException overflow) {
                throw conflict("E2A_ORDER_SCAN_SEQUENCE_CONFLICT");
            }
        }
        for (var scan : SubmissionJson.array(payload, "scans")) {
            UUID captureId = SubmissionJson.uuid(scan, "captureId");
            Long sequence = sequences.get(captureId);
            require(sequence != null && sequence == SubmissionJson.number(scan, "orderScanSeq"),
                "E2A_ORDER_SCAN_SEQUENCE_CONFLICT");
        }
        return expected - 1;
    }

    private static void require(boolean valid, String code) {
        if (!valid) throw conflict(code);
    }

    private static BusinessException conflict(String code) {
        return new BusinessException(409, code, "原机订单扫码序列不可核验");
    }
}
