package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static com.acrylic.outbound.recovery.RecoveryPreparationParts.integer;

/** Native TEMP preparation semantics, independent of current database versions. */
final class RecoveryTemporaryMaterial {
    private RecoveryTemporaryMaterial() { }

    static void verify(JsonNode manifest, Map<String, JsonNode> parts, JsonNode payload) {
        require(RecoveryPreparationBinding.time(manifest, "expiresAt").equals(
            RecoveryPreparationBinding.time(manifest, "issuedAt").plusSeconds(172800)), "RECOVERY_TEMP_BASELINE_WINDOW");
        require(text(payload, "authorizationEpoch").equals(text(manifest.path("identity"), "authorizationEpoch")),
            "RECOVERY_TEMP_BASELINE_CAPTURE");
        for (var capture : SubmissionJson.array(payload, "captures"))
            require(uuid(capture, "snapshotId").equals(uuid(manifest, "inventorySnapshotId"))
                && revision(capture, "orderRevision") == revision(payload, "orderRevision"), "RECOVERY_TEMP_BASELINE_CAPTURE");
        policy(manifest, parts.get("policy"), payload); rules(parts.get("rules"));
        inventory(parts.get("inventory"));
        availability(parts.get("availability"));
    }

    private static void policy(JsonNode manifest, JsonNode policy, JsonNode payload) {
        fields(policy, "id version autoDeductInventory schemaVersion offlineHours shortShipmentPolicy concurrencyMode scanMode reason publishedBy publishedAt");
        uuid(policy, "id"); text(policy, "publishedBy"); RecoveryPreparationBinding.time(policy, "publishedAt");
        require(text(policy, "version").matches("[1-9][0-9]{0,17}") && text(policy, "version").equals(text(manifest, "policyVersion"))
            && text(payload, "policyVersion").equals(text(manifest, "policyVersion")), "RECOVERY_TEMP_POLICY_VERSION");
        require(integer(policy, "schemaVersion") == 1 && integer(policy, "offlineHours") == 48
            && "SUPERVISOR_APPROVAL".equals(text(policy, "shortShipmentPolicy"))
            && "PHYSICAL_SEPARATION".equals(text(policy, "concurrencyMode"))
            && "AUTO_SCAN_MANUAL_CONFIRM".equals(text(policy, "scanMode")), "RECOVERY_TEMP_POLICY_MODE");
        require(policy.path("autoDeductInventory").isBoolean() && payload.path("autoDeductInventory").isBoolean()
            && policy.path("autoDeductInventory").equals(payload.path("autoDeductInventory")), "RECOVERY_TEMP_POLICY_DEDUCTION");
        text(policy, "reason");
    }

    private static void rules(JsonNode rules) {
        fields(rules, "version barcodePattern deliveryCodePrefixLength weightUnit maxWeightDecimals scanMode");
        require(integer(rules, "version") == 1 && "[A-Z0-9]{11}".equals(text(rules, "barcodePattern"))
            && integer(rules, "deliveryCodePrefixLength") == 3 && "INTEGER_GRAMS".equals(text(rules, "weightUnit"))
            && integer(rules, "maxWeightDecimals") == 3 && "AUTO_SCAN_MANUAL_CONFIRM".equals(text(rules, "scanMode")),
            "RECOVERY_TEMP_RULES");
    }

    private static void inventory(JsonNode rows) {
        for (var row : rows) {
            fields(row, "model name barcode deliveryCode rawWeight weightGrams");
            require(text(row, "barcode").startsWith(text(row, "deliveryCode"))
                && text(row, "deliveryCode").matches("[A-Z0-9]{3}")
                && !text(row, "model").isBlank() && !text(row, "name").isBlank(), "RECOVERY_TEMP_INVENTORY_PRODUCT");
            String raw = text(row, "rawWeight");
            require(raw.length() <= 64 && raw.matches("[0-9]+(?:\\.[0-9]{1,3})?") && revision(row, "weightGrams") > 0,
                "RECOVERY_TEMP_INVENTORY_WEIGHT");
            require(new BigDecimal(raw).movePointRight(3).longValueExact() == revision(row, "weightGrams"),
                "RECOVERY_TEMP_INVENTORY_WEIGHT");
        }
    }

    private static void availability(JsonNode value) {
        fields(value, "streamType recoveryEpoch warehouseId highWatermark nextCursor items");
        String cursor = text(value, "nextCursor");
        String expected = "1|" + text(value, "recoveryEpoch") + "|" + text(value, "warehouseId") + "|BARCODE|" + revision(value, "highWatermark");
        require(cursor.equals(Base64.getUrlEncoder().withoutPadding().encodeToString(expected.getBytes(StandardCharsets.US_ASCII))),
            "RECOVERY_TEMP_AVAILABILITY_CURSOR");
        for (var row : SubmissionJson.array(value, "items")) {
            fields(row, "baleId barcode status dispatchCycle rowVersion");
            require(revision(row, "dispatchCycle") > 0 && revision(row, "rowVersion") > 0
                && Set.of("AVAILABLE", "UNAVAILABLE", "RESERVED", "QUARANTINED", "DISPATCHED").contains(text(row, "status")),
                "RECOVERY_TEMP_AVAILABILITY_STATE");
        }
    }
}
