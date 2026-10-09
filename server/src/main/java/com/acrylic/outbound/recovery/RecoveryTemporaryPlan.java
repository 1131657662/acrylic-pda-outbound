package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Immutable first-round TEMP local plan and original enabled product mappings. */
final class RecoveryTemporaryPlan {
    private RecoveryTemporaryPlan() { }

    static void verify(RecoverySourceIdentity source, JsonNode payload, JsonNode mappings) {
        var plan = payload.path("temporaryOrder");
        fields(plan, "clientOrderId creatorDeviceId authorizationId revision vehicleNo containerNo reason lines");
        require(uuid(plan, "clientOrderId").equals(source.clientOrderId()) && uuid(plan, "creatorDeviceId").equals(source.creatorDeviceId())
            && uuid(plan, "authorizationId").equals(source.qualificationId()), "RECOVERY_TEMP_PLAN_SOURCE");
        require(revision(plan, "revision") > 0 && revision(plan, "revision") <= Integer.MAX_VALUE
            && revision(plan, "revision") == revision(payload, "orderRevision"), "RECOVERY_TEMP_PLAN_REVISION");
        require(SubmissionJson.sha(plan).equals(text(payload, "planSha256")), "RECOVERY_TEMP_PLAN_HASH");
        bounded(plan, "vehicleNo", new Limit(50, true)); bounded(plan, "containerNo", new Limit(50, false));
        bounded(plan, "reason", new Limit(500, true)); bounded(payload, "temporaryNo", new Limit(64, true));
        var lines = lines(plan, mappings(mappings));
        counts(payload, lines);
    }

    private static void counts(JsonNode payload, Map<String, JsonNode> lines) {
        var actual = new HashMap<String, Long>();
        for (var scan : SubmissionJson.array(payload, "scans")) {
            var line = lines.get(text(scan, "deliveryCode"));
            require(line != null, "RECOVERY_TEMP_PLAN_SCAN");
            for (String field : List.of("model", "name"))
                require(line.path(field).equals(scan.path(field)), "RECOVERY_TEMP_PLAN_SCAN_PRODUCT");
            if ("ACTIVE".equals(text(scan, "status"))) actual.merge(text(scan, "deliveryCode"), 1L, Math::addExact);
        }
        actual.forEach((code, count) -> require(count <= revision(lines.get(code), "plannedCount"), "RECOVERY_TEMP_PLAN_OVERLOAD"));
        if (lines.entrySet().stream().anyMatch(line -> actual.getOrDefault(line.getKey(), 0L) < revision(line.getValue(), "plannedCount")))
            bounded(payload, "shortShipmentReason", new Limit(500, true));
    }

    private static Map<String, JsonNode> mappings(JsonNode rows) {
        var result = new HashMap<String, JsonNode>();
        require(rows.isArray(), "RECOVERY_TEMP_MAPPING");
        for (var row : rows) {
            fields(row, "id deliveryCode version productId model name enabled reason current createdAt");
            uuid(row, "id"); uuid(row, "productId"); RecoveryPreparationBinding.time(row, "createdAt"); text(row, "reason");
            String code = text(row, "deliveryCode");
            require(code.matches("[A-Z0-9]{3}") && result.put(code, row) == null && revision(row, "version") > 0
                && row.path("enabled").isBoolean() && row.path("current").isBoolean() && row.path("current").booleanValue()
                && !text(row, "model").isBlank() && !text(row, "name").isBlank(), "RECOVERY_TEMP_MAPPING");
        }
        return result;
    }

    private static Map<String, JsonNode> lines(JsonNode plan, Map<String, JsonNode> mappings) {
        var result = new HashMap<String, JsonNode>(); String previous = "";
        var lines = SubmissionJson.array(plan, "lines");
        require(!lines.isEmpty() && lines.size() <= 500, "RECOVERY_TEMP_PLAN_LINES");
        for (var line : lines) {
            fields(line, "deliveryCode productId model name plannedCount");
            String code = text(line, "deliveryCode"); var mapping = mappings.get(code);
            require(code.matches("[0-9]{3}") && code.compareTo(previous) > 0 && mapping != null && mapping.path("enabled").booleanValue(),
                "RECOVERY_TEMP_PLAN_MAPPING");
            previous = code;
            for (String field : List.of("productId", "model", "name"))
                require(line.path(field).equals(mapping.path(field)), "RECOVERY_TEMP_PLAN_PRODUCT");
            long count = revision(line, "plannedCount");
            require(count > 0 && count <= Integer.MAX_VALUE, "RECOVERY_TEMP_PLAN_COUNT"); result.put(code, line);
        }
        return result;
    }

    private static void bounded(JsonNode value, String key, Limit limit) {
        String raw = text(value, key);
        require(raw.length() <= limit.maximum() && (!limit.required() || !raw.isBlank()) && raw.equals(raw.strip())
            && raw.chars().noneMatch(Character::isISOControl), "RECOVERY_TEMP_PLAN_TEXT");
    }

    private record Limit(int maximum, boolean required) { }
}
