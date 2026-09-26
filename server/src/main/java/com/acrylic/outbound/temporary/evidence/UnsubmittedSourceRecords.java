package com.acrylic.outbound.temporary.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Validates a snapshot state without inventing a freeze, plan approval or physical disposition. */
final class UnsubmittedSourceRecords {
    private UnsubmittedSourceRecords() { }
    private static final Set<String> STATES = Set.of("RECEIVED", "WAITING_CONFIRM", "CONFIRM_REQUESTED",
        "COMMITTED", "INVALID", "BUSINESS_REJECTED", "DISCARDED");

    static void validate(JsonNode capture, JsonNode scan, List<JsonNode> events, UnsubmittedSourceAuthority.Origin origin) {
        capture(capture);
        var history = UnsubmittedSourceTransitions.validate(capture, events);
        if (!capture.path("confirmedAt").isNull()) {
            var confirmed = Instant.parse(text(capture, "confirmedAt"));
            require(!confirmed.isBefore(origin.issued()) && confirmed.isBefore(origin.expires()), "确认超出原授权期限");
        }
        String state = text(capture, "state");
        if (Set.of("WAITING_CONFIRM", "CONFIRM_REQUESTED", "COMMITTED").contains(state)) baseline(capture, origin);
        require("COMMITTED".equals(state) == (scan != null), "其他来源保存明细缺失或伪造");
        if (scan == null) return;
        scan(capture, scan, origin);
        var saved = history.get("SAVED");
        require(saved != null && number(saved, "localSeq") == number(scan, "localSeq"), "原保存事件不符");
        require(Set.of("ACTIVE", "VOID").contains(text(scan, "status")), "原明细状态无效");
        require("VOID".equals(text(scan, "status")) == history.containsKey("VOID"), "原撤销事实不符");
    }

    private static void baseline(JsonNode capture, UnsubmittedSourceAuthority.Origin origin) {
        String barcode = text(capture, "barcode");
        require(barcode.matches("[A-Z0-9]{11}") && origin.parts().inventory().containsKey(barcode), "原已验证输入不在库存内");
        var available = origin.parts().availability().get(barcode);
        require(available != null, "原已验证输入缺周期基线");
        equal(number(capture, "expectedDispatchCycle"), number(available, "dispatchCycle"), "原输入周期不符");
    }

    private static void capture(JsonNode row) {
        require(STATES.contains(text(row, "state")), "原输入状态无效");
        require(number(row, "orderScanSeq") > 0 && number(row, "deviceCaptureSeq") > 0, "原输入序号无效");
        require(Set.of("MANUAL", "BROADCAST").contains(text(row, "inputSource")), "原输入方式无效");
        equal(text(row, "confirmationPolicy"), "BROADCAST".equals(text(row, "inputSource")) ? "AUTO_AFTER_VALIDATION" : "MANUAL", "原确认策略不符");
        require(text(row, "rawInput").length() <= 128 && text(row, "error").length() <= 500, "原输入过长");
        equal(text(row, "rawInput").replaceAll("^[ \\r\\n\\t]+|[ \\r\\n\\t]+$", "").toUpperCase(Locale.ROOT),
            text(row, "barcode"), "原始输入条码不符");
        number(row, "receivedWallMs"); number(row, "receivedElapsedMs");
        require(row.path("receivedBoot").isInt(), "原输入启动标识错误");
        require(row.has("confirmedAt") && row.has("failureAckAt") && row.has("expectedDispatchCycle"), "原输入字段缺失");
        boolean confirmed = Set.of("CONFIRM_REQUESTED", "COMMITTED", "BUSINESS_REJECTED").contains(text(row, "state"));
        require(confirmed == !row.path("confirmedAt").isNull(), "原确认状态不符");
        if ("DISCARDED".equals(text(row, "state"))) equal(text(row, "inputSource"), "MANUAL", "自动输入不得放弃");
    }

    private static void scan(JsonNode capture, JsonNode scan, UnsubmittedSourceAuthority.Origin origin) {
        equal(uuid(scan, "captureId"), uuid(capture, "id"), "原明细输入不符");
        equal(number(scan, "orderScanSeq"), number(capture, "orderScanSeq"), "原明细顺序不符");
        equal(text(scan, "barcode"), text(capture, "barcode"), "原明细条码不符");
        require(text(scan, "barcode").matches("[A-Z0-9]{11}"), "有效原条码格式错误");
        var inventory = origin.parts().inventory().get(text(scan, "barcode"));
        var available = origin.parts().availability().get(text(scan, "barcode"));
        require(inventory != null && available != null, "其他来源原库存缺失");
        for (String field : List.of("deliveryCode", "model", "name", "rawWeight", "weightGrams"))
            equal(text(scan, field), text(inventory, field), "其他来源原库存明细不符");
        equal(uuid(scan, "baleId"), uuid(available, "baleId"), "其他来源原包ID不符");
        equal(number(scan, "expectedDispatchCycle"), number(available, "dispatchCycle"), "其他来源原周期不符");
        equal(number(scan, "expectedDispatchCycle"), number(capture, "expectedDispatchCycle"), "原输入周期不符");
        require(number(scan, "expectedDispatchCycle") > 0, "原周期无效");
        Instant confirmed = Instant.parse(text(capture, "confirmedAt")), saved = Instant.parse(text(scan, "savedAt"));
        require(!confirmed.isBefore(origin.issued()) && !saved.isBefore(confirmed) && saved.isBefore(origin.expires()), "保存超出原授权期限");
    }
}
