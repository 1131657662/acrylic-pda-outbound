package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Binds the complete single-source input/order sequences to the independently retained device watermarks. */
final class RecoveryInitialRecords {
    private RecoveryInitialRecords() { }

    static void verify(RecoveryPinnedSource source, List<JsonNode> events) {
        var body = SubmissionJson.parse(source.original("payload")); var identity = source.identity();
        require(body.path("schemaVersion").isInt() && body.path("schemaVersion").intValue() == 2,
            "RECOVERY_INITIAL_SCHEMA");
        require(uuid(body, "submissionId").equals(identity.submissionId()), "RECOVERY_INITIAL_SUBMISSION");
        source(body.path("source"), identity);
        var captures = new LinkedHashMap<UUID, JsonNode>(); long order = 0, device = 0;
        for (var capture : SubmissionJson.array(body, "captures")) {
            require(revision(capture, "orderScanSeq") == ++order, "RECOVERY_INITIAL_ORDER_SEQUENCE");
            long next = revision(capture, "deviceCaptureSeq");
            require(next > device, "RECOVERY_INITIAL_DEVICE_SEQUENCE"); device = next;
            require(captures.put(uuid(capture, "id"), capture) == null, "RECOVERY_SOURCE_CAPTURE_DUPLICATE");
            capture(capture, body);
        }
        require(order == identity.orderScanHighWatermark() && revision(body, "captureHighWatermark") == order
            && device == identity.captureHighWatermark(), "RECOVERY_INITIAL_WATERMARK");
        var current = SubmissionJson.array(body, "events");
        require(current.size() == events.size(), "RECOVERY_INITIAL_AUDIT_SET");
        for (int index = 0; index < events.size(); index++)
            require(current.get(index).equals(events.get(index)), "RECOVERY_INITIAL_AUDIT_ORDER");
        RecoveryCaptureHistory.verify(body, captures, events);
        summary(body, captures.values());
    }

    private static void source(JsonNode value, RecoverySourceIdentity identity) {
        if (identity.sourceKind().equals("FORMAL")) {
            fields(value, "kind sourceOrderId");
            require(uuid(value, "sourceOrderId").equals(identity.canonicalOrderId()), "RECOVERY_INITIAL_SOURCE");
        } else {
            fields(value, "kind creatorDeviceId clientOrderId");
            require(uuid(value, "creatorDeviceId").equals(identity.creatorDeviceId())
                && uuid(value, "clientOrderId").equals(identity.clientOrderId()), "RECOVERY_INITIAL_SOURCE");
        }
        require(text(value, "kind").equals(identity.sourceKind()), "RECOVERY_INITIAL_SOURCE");
    }

    private static void capture(JsonNode capture, JsonNode body) {
        String input = text(capture, "inputSource"), state = text(capture, "state");
        require(Set.of("MANUAL", "BROADCAST").contains(input)
            && text(capture, "confirmationPolicy").equals(input.equals("MANUAL") ? "MANUAL" : "AUTO_AFTER_VALIDATION"),
            "RECOVERY_INITIAL_INPUT");
        require(text(capture, "rawInput").length() <= 128 && text(capture, "error").length() <= 500
            && capture.path("receivedBoot").isInt(), "RECOVERY_INITIAL_INPUT");
        revision(capture, "receivedWallMs"); revision(capture, "receivedElapsedMs");
        require(capture.has("confirmedAt") && capture.has("failureAckAt") && capture.has("expectedDispatchCycle"),
            "RECOVERY_INITIAL_CAPTURE_FIELDS");
        boolean confirmed = Set.of("COMMITTED", "BUSINESS_REJECTED").contains(state);
        require(confirmed == capture.hasNonNull("confirmedAt"), "RECOVERY_INITIAL_CONFIRMATION");
        if (capture.has("clientOrderId"))
            require(capture.path("snapshotId").equals(body.path("inventorySnapshotId"))
                && capture.path("orderRevision").equals(body.path("orderRevision")), "RECOVERY_INITIAL_BASELINE");
    }

    private static void summary(JsonNode body, Collection<JsonNode> captures) {
        var scans = SubmissionJson.array(body, "scans"); long active = 0, voided = 0;
        for (var scan : scans) {
            if ("ACTIVE".equals(text(scan, "status"))) active++;
            else { require("VOID".equals(text(scan, "status")), "RECOVERY_INITIAL_SCAN_STATUS"); voided++; }
        }
        var summary = body.path("captureSummary");
        require(revision(summary, "received") == captures.size() && revision(summary, "saved") == active
            && revision(summary, "voided") == voided && revision(summary, "pending") == 0
            && revision(body, "expectedCount") == active, "RECOVERY_INITIAL_SUMMARY");
        Map.of("invalid", "INVALID", "businessRejected", "BUSINESS_REJECTED", "discarded", "DISCARDED")
            .forEach((field, state) -> require(revision(summary, field) == captures.stream()
                .filter(capture -> state.equals(text(capture, "state"))).count(), "RECOVERY_INITIAL_SUMMARY"));
    }
}
