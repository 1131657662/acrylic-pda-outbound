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
        return require(payload, creatorDeviceId, null);
    }

    static long require(JsonNode payload, UUID creatorDeviceId, JsonNode verifiedPrefix) {
        int prefixSize = verifiedPrefix == null ? 0 : SubmissionJson.array(verifiedPrefix, "captures").size();
        if (verifiedPrefix != null) requirePrefix(payload, verifiedPrefix);
        var sequences = new HashMap<UUID, Long>();
        long expected = 1;
        for (var capture : SubmissionJson.array(payload, "captures")) {
            require(expected <= prefixSize || SubmissionJson.uuid(capture, "originDeviceId").equals(creatorDeviceId),
                "E2A_ORDER_SCAN_SOURCE_CONFLICT");
            long sequence = SubmissionJson.number(capture, "orderScanSeq");
            require(sequence == expected, "E2A_ORDER_SCAN_SEQUENCE_CONFLICT");
            require(sequences.put(SubmissionJson.uuid(capture, "id"), sequence) == null,
                "E2A_ORDER_SCAN_SEQUENCE_CONFLICT");
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

    private static void requirePrefix(JsonNode payload, JsonNode prefix) {
        for (String field : java.util.List.of("captures", "scans", "events")) {
            var original = SubmissionJson.array(prefix, field);
            var current = SubmissionJson.array(payload, field);
            require(current.size() >= original.size(), "E2A_ORDER_SCAN_PREFIX_CONFLICT");
            for (int index = 0; index < original.size(); index++)
                require(original.get(index).equals(current.get(index)), "E2A_ORDER_SCAN_PREFIX_CONFLICT");
        }
    }

    private static void require(boolean valid, String code) {
        if (!valid) throw conflict(code);
    }

    private static BusinessException conflict(String code) {
        return new BusinessException(409, code, "原机订单扫码序列不可核验");
    }
}
