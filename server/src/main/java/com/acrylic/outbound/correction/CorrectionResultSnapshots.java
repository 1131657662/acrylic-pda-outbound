package com.acrylic.outbound.correction;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;

/** Freezes result state independently of inventory contributions. */
final class CorrectionResultSnapshots {
    private final CorrectionStore store;
    CorrectionResultSnapshots(CorrectionStore store) { this.store = store; }

    long[] totals(Map<String, Object> current, List<Map<String, Object>> lines, boolean withdraw) {
        long count = number(current, "actual_count"), weight = number(current, "weight_g");
        for (var line : lines) {
            long c = Math.subtractExact(number(line, "target_count"), number(line, "before_count"));
            long w = Math.subtractExact(number(line, "target_weight_g"), number(line, "before_weight_g"));
            count = Math.addExact(count, withdraw ? Math.negateExact(c) : c);
            weight = Math.addExact(weight, withdraw ? Math.negateExact(w) : w);
        }
        if (count < 0 || weight < 0) throw CorrectionCommand.ledgerConflict();
        return new long[]{count, weight};
    }

    void version(CorrectionCommand.Write write, Map<String, Object> current, ObjectNode result) {
        String original = String.valueOf(current.get("original_submission_id"));
        store.insertResultVersion(write.orderId(), result.path("resultVersion").asLong(), "null".equals(original) ? null
            : UUID.fromString(original), write.documentId(), result.path("effectiveCount").asLong(),
            result.path("effectiveWeightG").asLong(), flag(current.get("inventory_deducted")),
            result.path("headerSnapshot").asText(), write.recordedAt());
    }

    void scans(CorrectionCommand.Write write, long version, List<Map<String, Object>> lines, boolean withdraw) {
        var changed = new HashMap<String, Map<String, Object>>();
        for (var line : lines) if (line.get("dispatch_id") != null) changed.put(line.get("dispatch_id").toString(), line);
        var previous = scanIndex(write.orderId(), version - 1);
        var restored = withdraw ? scanIndex(write.orderId(), write.anchor().path("expectedResultVersion").asLong()) : Map.<String, Map<String, Object>>of();
        var aggregate = new LinkedHashMap<String, long[]>();
        for (var dispatch : store.dispatchesForUpdate(write.orderId())) {
            String id = dispatch.get("dispatch_id").toString(); var line = changed.get(id);
            var before = store.dispatchFacts(write.orderId(), UUID.fromString(id));
            String side = withdraw ? "before" : "target";
            long count = line == null ? before.path("count").asLong() : number(line, side + "_count");
            long weight = line == null ? before.path("weightG").asLong() : number(line, side + "_weight_g");
            UUID latest = line == null ? latest(previous.get(id)) : withdraw ? latest(restored.get(id)) : UUID.fromString(line.get("line_id").toString());
            store.insertEffectiveScan(write.orderId(), version, UUID.fromString(id),
                UUID.fromString(dispatch.get("capture_id").toString()), number(dispatch, "scan_seq"), count > 0, weight, latest);
            var total = aggregate.computeIfAbsent(dispatch.get("delivery_code").toString(), key -> new long[2]);
            if (count > 0) { total[0]++; total[1] = Math.addExact(total[1], weight); }
        }
        aggregate.forEach((delivery, total) -> store.insertEffectiveLine(write.orderId(), version, delivery, total[0], total[1]));
    }

    void requireTargets(UUID order, List<Map<String, Object>> lines) {
        var scans = scanIndex(order, store.orderFacts(order).path("currentResultVersion").asLong());
        for (var line : lines) {
            if (line.get("dispatch_id") == null) continue;
            String dispatch = line.get("dispatch_id").toString();
            var facts = store.dispatchFacts(order, UUID.fromString(dispatch));
            if (facts.path("count").asLong() != number(line, "target_count")
                || facts.path("weightG").asLong() != number(line, "target_weight_g")
                || !UUID.fromString(line.get("line_id").toString()).equals(latest(scans.get(dispatch))))
                throw CorrectionCommand.ledgerConflict();
        }
    }

    private Map<String, Map<String, Object>> scanIndex(UUID order, long version) {
        var result = new HashMap<String, Map<String, Object>>();
        for (var scan : store.effectiveScan(order, version)) result.put(scan.get("dispatch_id").toString(), scan);
        return result;
    }

    private static UUID latest(Map<String, Object> scan) {
        String value = scan == null ? "null" : String.valueOf(scan.get("latest_correction_line_id"));
        return "null".equals(value) ? null : UUID.fromString(value);
    }

    void outbox(CorrectionCommand.Write write, ObjectNode result, String type) {
        var payload = result.deepCopy(); payload.remove("headerSnapshot");
        payload.put("status", store.orderFacts(write.orderId()).path("orderStatus").asText());
        long epoch = store.recoveryEpoch();
        store.insertOutbox(write.orderId(), epoch, type, store.nextOutboxEntityVersion(write.orderId(), epoch),
            SubmissionJson.canonical(payload), write.recordedAt());
    }

    static long number(Map<String, Object> row, String key) { return Long.parseLong(row.get(key).toString()); }
    static boolean flag(Object value) { return value instanceof Number n ? n.intValue() != 0 : Boolean.parseBoolean(value.toString()); }
    static Instant instant(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.LocalDateTime local) return local.toInstant(java.time.ZoneOffset.UTC);
        if (value instanceof Instant instant) return instant;
        throw CorrectionCommand.ledgerConflict();
    }
}
