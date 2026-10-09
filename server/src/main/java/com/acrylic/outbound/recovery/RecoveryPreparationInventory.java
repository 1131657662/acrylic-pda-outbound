package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static com.acrylic.outbound.recovery.RecoveryPreparationParts.integer;

/** Original scan baseline only; later stock conflicts, policy and preparation ACK remain separate. */
final class RecoveryPreparationInventory {
    private RecoveryPreparationInventory() { }

    static void verify(RecoverySourceIdentity source, JsonNode payload, Material material) {
        boolean temporary = source.sourceKind().equals("TEMP");
        var inventory = rows(material.parts().get("inventory"));
        JsonNode available = material.parts().get("availability");
        if (temporary) {
            require(text(available, "warehouseId").equals(source.warehouseId().toString())
                && text(available, "recoveryEpoch").equals(source.sourceRecoveryEpoch()) && "BARCODE".equals(text(available, "streamType")),
                "RECOVERY_SOURCE_PREPARATION_AVAILABILITY");
            revision(available, "highWatermark"); available = available.path("items");
        }
        var availability = rows(available);
        require(temporary ? availability.keySet().containsAll(inventory.keySet()) : availability.keySet().equals(inventory.keySet()),
            "RECOVERY_SOURCE_PREPARATION_ROW_SET");
        var bales = new HashSet<UUID>();
        for (var row : availability.values()) require(bales.add(uuid(row, "baleId")), "RECOVERY_SOURCE_PREPARATION_BALE_DUPLICATE");
        var context = new ScanContext(inventory, availability, temporary);
        var counts = scans(payload, context);
        if (!temporary) plan(source, material, counts);
        times(payload, RecoveryPreparationBinding.time(material.manifest(), temporary ? "issuedAt" : "stateCapturedAt"));
    }

    private static Map<String, Long> scans(JsonNode payload, ScanContext context) {
        var counts = new HashMap<String, Long>(); var activeBales = new HashSet<UUID>(); long grams = 0; long count = 0;
        for (var scan : SubmissionJson.array(payload, "scans")) {
            scan(scan, context);
            if ("ACTIVE".equals(text(scan, "status"))) {
                require(activeBales.add(uuid(scan, "baleId")), "RECOVERY_SOURCE_PREPARATION_SCAN_DUPLICATE"); count++;
                counts.merge(text(scan, "deliveryCode"), 1L, Math::addExact);
                grams = Math.addExact(grams, revision(scan, "weightGrams"));
            }
        }
        require(grams == revision(payload, "expectedWeightGrams") && count == revision(payload, "expectedCount"),
            "RECOVERY_SOURCE_PREPARATION_TOTAL");
        return counts;
    }

    private static Map<String, JsonNode> rows(JsonNode array) {
        require(array.isArray(), "RECOVERY_SOURCE_PREPARATION_ROWS");
        var result = new HashMap<String, JsonNode>();
        for (var row : array) {
            String barcode = text(row, "barcode");
            require(barcode.matches("[A-Z0-9]{11}") && result.put(barcode, row) == null, "RECOVERY_SOURCE_PREPARATION_BARCODE");
        }
        return result;
    }

    private static void scan(JsonNode scan, ScanContext context) {
        var original = context.inventory().get(text(scan, "barcode")); var state = context.availability().get(text(scan, "barcode"));
        require(original != null && state != null, "RECOVERY_SOURCE_PREPARATION_SCAN_MISSING");
        require(uuid(scan, "baleId").equals(uuid(state, "baleId"))
            && revision(scan, "expectedDispatchCycle") > 0
            && revision(scan, "expectedDispatchCycle") == revision(state, "dispatchCycle"), "RECOVERY_SOURCE_PREPARATION_CYCLE");
        for (String field : List.of("deliveryCode", "model", "name", "rawWeight", "weightGrams"))
            require(text(scan, field).equals(text(original, field)), "RECOVERY_SOURCE_PREPARATION_SCAN_BASELINE");
        require(revision(scan, "weightGrams") > 0 && Set.of("ACTIVE", "VOID").contains(text(scan, "status")),
            "RECOVERY_SOURCE_PREPARATION_SCAN_BASELINE");
        boolean available = context.temporary() ? "AVAILABLE".equals(text(state, "status"))
            : state.path("available").isBoolean() && state.path("available").booleanValue();
        require(available, "RECOVERY_SOURCE_PREPARATION_AVAILABILITY");
    }

    private static void plan(RecoverySourceIdentity source, Material material, Map<String, Long> counts) {
        JsonNode plan = material.parts().get("plan"); var order = plan.path("order"); var revision = order.path("plan");
        require(uuid(order.path("order"), "id").equals(source.canonicalOrderId())
            && text(plan, "serverRecoveryEpoch").equals(source.sourceRecoveryEpoch())
            && uuid(revision, "inventorySnapshotId").equals(uuid(material.manifest(), "inventorySnapshotId"))
            && integer(revision, "revision") == integer(material.manifest(), "revision"), "RECOVERY_SOURCE_PREPARATION_PLAN");
        var products = new HashMap<String, Long>();
        for (var line : SubmissionJson.array(order, "lines"))
            require(integer(line, "plannedCount") > 0 && products.put(text(line, "deliveryCode"), integer(line, "plannedCount")) == null,
                "RECOVERY_SOURCE_PREPARATION_PLAN");
        require(products.keySet().containsAll(counts.keySet()), "RECOVERY_SOURCE_PREPARATION_PLAN");
        counts.forEach((code, count) -> require(count <= products.get(code), "RECOVERY_SOURCE_PREPARATION_PLAN"));
    }

    private static void times(JsonNode payload, Instant prepared) {
        for (var capture : SubmissionJson.array(payload, "captures"))
            if (capture.hasNonNull("confirmedAt")) require(!Instant.parse(text(capture, "confirmedAt")).isBefore(prepared),
                "RECOVERY_SOURCE_PREPARATION_CAPTURE_TIME");
        for (var scan : SubmissionJson.array(payload, "scans")) require(!Instant.parse(text(scan, "savedAt")).isBefore(prepared),
            "RECOVERY_SOURCE_PREPARATION_CAPTURE_TIME");
    }

    record Material(JsonNode manifest, Map<String, JsonNode> parts) { }
    private record ScanContext(Map<String, JsonNode> inventory, Map<String, JsonNode> availability, boolean temporary) { }
}
