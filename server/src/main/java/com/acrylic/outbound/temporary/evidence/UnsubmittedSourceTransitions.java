package com.acrylic.outbound.temporary.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Reconstructs capture state from its original pre-stop events. */
final class UnsubmittedSourceTransitions {
    private UnsubmittedSourceTransitions() { }

    static Map<String, JsonNode> validate(JsonNode capture, List<JsonNode> events) {
        var history = new HashMap<String, JsonNode>();
        String state = ""; long sequence = 0;
        for (var event : events) {
            source(capture, event);
            require(number(event, "localSeq") > sequence, "其他来源审计顺序错误");
            sequence = number(event, "localSeq");
            String type = text(event, "type");
            require(history.put(type, event) == null, "其他来源状态事件重复");
            state = transition(state, type);
        }
        equal(state, text(capture, "state"), "其他来源原输入与审计状态不符");
        require(history.containsKey("FAILURE_ACKNOWLEDGED") == !capture.path("failureAckAt").isNull(), "原失败确认不符");
        if (!capture.path("failureAckAt").isNull()) Instant.parse(text(capture, "failureAckAt"));
        if ("WAITING_CONFIRM".equals(state) || "DISCARDED".equals(state))
            equal(text(capture, "inputSource"), "MANUAL", "原手输状态不符");
        if (history.containsKey("CONFIRM_REQUESTED"))
            require(history.containsKey("WAITING_CONFIRM") == "MANUAL".equals(text(capture, "inputSource")), "原确认路径不符");
        for (String type : List.of("INVALID", "BUSINESS_REJECTED", "DISCARDED", "FAILURE_ACKNOWLEDGED"))
            if (history.containsKey(type)) equal(text(history.get(type), "detail"), text(capture, "error"), "原失败或放弃原因不符");
        return history;
    }

    private static String transition(String state, String type) {
        boolean valid = switch (type) {
            case "RECEIVED" -> state.isEmpty();
            case "WAITING_CONFIRM", "INVALID" -> "RECEIVED".equals(state);
            case "CONFIRM_REQUESTED" -> Set.of("RECEIVED", "WAITING_CONFIRM").contains(state);
            case "SAVED", "BUSINESS_REJECTED" -> "CONFIRM_REQUESTED".equals(state);
            case "DISCARDED" -> Set.of("RECEIVED", "WAITING_CONFIRM").contains(state);
            case "VOID" -> "COMMITTED".equals(state);
            case "FAILURE_ACKNOWLEDGED" -> Set.of("INVALID", "BUSINESS_REJECTED").contains(state);
            default -> false;
        };
        require(valid, "其他来源审计状态迁移不符");
        return switch (type) {
            case "SAVED", "VOID" -> "COMMITTED";
            case "FAILURE_ACKNOWLEDGED" -> state;
            default -> type;
        };
    }

    private static void source(JsonNode capture, JsonNode event) {
        for (String field : List.of("originDeviceId", "operatorId"))
            equal(uuid(event, field), uuid(capture, field), "其他来源审计原身份不符");
        equal(uuid(event, "captureId"), uuid(capture, "id"), "其他来源审计输入不符");
        if (capture.has("clientOrderId")) {
            equal(text(event.path("source"), "kind"), "TEMP", "其他来源审计类型不符");
            equal(uuid(event.path("source"), "clientOrderId"), uuid(capture, "clientOrderId"), "其他来源审计临时单不符");
            equal(uuid(event.path("source"), "creatorDeviceId"), uuid(capture, "originDeviceId"), "其他来源审计设备不符");
            equal(uuid(event, "authorizationId"), uuid(capture, "authorizationId"), "其他来源审计资格不符");
        } else {
            require(!event.has("source"), "其他来源审计类型不符");
            equal(uuid(event, "assignmentId"), uuid(capture, "assignmentId"), "其他来源审计授权不符");
        }
    }
}
