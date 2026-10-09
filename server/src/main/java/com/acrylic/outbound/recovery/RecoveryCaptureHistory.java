package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Replays original per-capture audit states without synthesizing a STOP, ACK or physical action. */
final class RecoveryCaptureHistory {
    private RecoveryCaptureHistory() { }

    static void verify(JsonNode body, Map<UUID, JsonNode> captures, List<JsonNode> events) {
        var grouped = new HashMap<UUID, List<JsonNode>>();
        for (var event : events) {
            if (event.path("captureId").isNull()) continue;
            UUID id = uuid(event, "captureId");
            require(captures.containsKey(id), "RECOVERY_INITIAL_AUDIT_CAPTURE");
            grouped.computeIfAbsent(id, ignored -> new ArrayList<>()).add(event);
        }
        var histories = new HashMap<UUID, Map<String, JsonNode>>();
        captures.forEach((id, capture) -> histories.put(id, replay(capture, grouped.getOrDefault(id, List.of()))));
        var saved = new HashSet<UUID>();
        for (var scan : SubmissionJson.array(body, "scans")) {
            UUID id = uuid(scan, "captureId"); var history = histories.get(id);
            require(saved.add(id) && history != null && history.containsKey("SAVED"), "RECOVERY_INITIAL_SAVE_EVENT");
            require(revision(scan, "localSeq") == revision(history.get("SAVED"), "localSeq"), "RECOVERY_INITIAL_SAVE_EVENT");
            require("VOID".equals(text(scan, "status")) == history.containsKey("VOID"), "RECOVERY_INITIAL_VOID_EVENT");
            String raw = text(captures.get(id), "rawInput").replaceAll("^[ \\r\\n\\t]+|[ \\r\\n\\t]+$", "").toUpperCase(Locale.ROOT);
            require(raw.matches("[A-Z0-9]{11}") && raw.equals(text(scan, "barcode")), "RECOVERY_INITIAL_RAW_INPUT");
        }
    }

    private static Map<String, JsonNode> replay(JsonNode capture, List<JsonNode> events) {
        var history = new HashMap<String, JsonNode>(); String state = "";
        for (var event : events) {
            origin(capture, event);
            String type = text(event, "type");
            require(history.put(type, event) == null, "RECOVERY_INITIAL_AUDIT_TRANSITION");
            state = transition(state, type);
        }
        require(state.equals(text(capture, "state")), "RECOVERY_INITIAL_AUDIT_STATE");
        require(history.containsKey("FAILURE_ACKNOWLEDGED") == capture.hasNonNull("failureAckAt"),
            "RECOVERY_INITIAL_FAILURE_ACK");
        if (capture.hasNonNull("failureAckAt")) Instant.parse(text(capture, "failureAckAt"));
        boolean failed = Set.of("INVALID", "BUSINESS_REJECTED").contains(state);
        require(!failed || history.containsKey("FAILURE_ACKNOWLEDGED"), "RECOVERY_INITIAL_FAILURE_ACK");
        if (history.containsKey("CONFIRM_REQUESTED"))
            require(history.containsKey("WAITING_CONFIRM") == "MANUAL".equals(text(capture, "inputSource")),
                "RECOVERY_INITIAL_CONFIRM_EVENT");
        if (state.equals("DISCARDED")) require("MANUAL".equals(text(capture, "inputSource")), "RECOVERY_INITIAL_CONFIRM_EVENT");
        for (String type : List.of("INVALID", "BUSINESS_REJECTED", "DISCARDED", "FAILURE_ACKNOWLEDGED"))
            if (history.containsKey(type)) require(text(history.get(type), "detail").equals(text(capture, "error")),
                "RECOVERY_INITIAL_FAILURE_DETAIL");
        return Map.copyOf(history);
    }

    private static String transition(String state, String type) {
        boolean valid = switch (type) {
            case "RECEIVED" -> state.isEmpty();
            case "WAITING_CONFIRM", "INVALID" -> state.equals("RECEIVED");
            case "CONFIRM_REQUESTED" -> Set.of("RECEIVED", "WAITING_CONFIRM").contains(state);
            case "SAVED", "BUSINESS_REJECTED" -> state.equals("CONFIRM_REQUESTED");
            case "DISCARDED" -> Set.of("RECEIVED", "WAITING_CONFIRM").contains(state);
            case "VOID" -> state.equals("COMMITTED");
            case "FAILURE_ACKNOWLEDGED" -> Set.of("INVALID", "BUSINESS_REJECTED").contains(state);
            default -> false;
        };
        require(valid, "RECOVERY_INITIAL_AUDIT_TRANSITION");
        return switch (type) {
            case "SAVED", "VOID" -> "COMMITTED";
            case "FAILURE_ACKNOWLEDGED" -> state;
            default -> type;
        };
    }

    private static void origin(JsonNode capture, JsonNode event) {
        for (String field : List.of("originDeviceId", "operatorId"))
            require(uuid(capture, field).equals(uuid(event, field)), "RECOVERY_INITIAL_AUDIT_ORIGIN");
        if (capture.has("clientOrderId"))
            require(uuid(capture, "authorizationId").equals(uuid(event, "authorizationId"))
                && uuid(capture, "clientOrderId").equals(uuid(event.path("source"), "clientOrderId")), "RECOVERY_INITIAL_AUDIT_ORIGIN");
        else require(uuid(capture, "assignmentId").equals(uuid(event, "assignmentId")), "RECOVERY_INITIAL_AUDIT_ORIGIN");
    }
}
