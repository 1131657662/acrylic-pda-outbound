package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native nonempty TEMP material and local plan, with synthetic control and development signatures. */
final class RecoveryTemporaryBaselineFixture {
    final RecoveryPreparationFixture prep = new RecoveryPreparationFixture("TEMP");
    final ObjectNode policy;
    final ObjectNode rules;
    final ObjectNode mapping;
    final ObjectNode plan;

    RecoveryTemporaryBaselineFixture() throws Exception {
        var id = prep.state.authority.source.identity;
        policy = SubmissionJson.object().put("id", UUID.randomUUID().toString()).put("version", "1").put("autoDeductInventory", false)
            .put("schemaVersion", 1).put("offlineHours", 48).put("shortShipmentPolicy", "SUPERVISOR_APPROVAL")
            .put("concurrencyMode", "PHYSICAL_SEPARATION").put("scanMode", "AUTO_SCAN_MANUAL_CONFIRM").put("reason", "fixture")
            .put("publishedBy", "Operator").put("publishedAt", "2026-09-20T00:00:00Z");
        rules = SubmissionJson.object().put("version", 1).put("barcodePattern", "[A-Z0-9]{11}").put("deliveryCodePrefixLength", 3)
            .put("weightUnit", "INTEGER_GRAMS").put("maxWeightDecimals", 3).put("scanMode", "AUTO_SCAN_MANUAL_CONFIRM");
        mapping = SubmissionJson.object().put("id", UUID.randomUUID().toString()).put("deliveryCode", "012").put("version", "1")
            .put("productId", UUID.randomUUID().toString()).put("model", "MODEL").put("name", "Product").put("enabled", true)
            .put("reason", "fixture").put("current", true).put("createdAt", "2026-09-20T00:00:00Z");
        var mappings = SubmissionJson.object().putArray("rows").add(mapping);
        prep.content.put("mapping", mappings); prep.content.put("policy", policy); prep.content.put("rules", rules);
        plan = prep.state.payload.putObject("temporaryOrder").put("clientOrderId", id.clientOrderId().toString())
            .put("creatorDeviceId", id.creatorDeviceId().toString()).put("authorizationId", id.qualificationId().toString())
            .put("revision", "1").put("vehicleNo", "V-1").put("containerNo", "C-1").put("reason", "fixture");
        plan.putArray("lines").addObject().put("deliveryCode", "012").put("productId", mapping.path("productId").asText())
            .put("model", "MODEL").put("name", "Product").put("plannedCount", "1");
        prep.state.payload.put("orderRevision", "1").put("temporaryNo", "TEMP-1").put("policyVersion", "1").put("autoDeductInventory", false)
            .put("authorizationEpoch", prep.manifest.at("/identity/authorizationEpoch").asText());
        ((ObjectNode)prep.state.payload.path("captures").get(0)).put("snapshotId", prep.manifest.path("inventorySnapshotId").asText())
            .put("orderRevision", "1");
        prep.manifest.put("expiresAt", "2026-09-22T00:00:00Z");
        prep.state.authority.claims.put("exp", java.time.Instant.parse("2026-09-22T00:00:00Z").getEpochSecond());
        var cursor = "1|" + id.sourceRecoveryEpoch() + "|" + id.warehouseId() + "|BARCODE|0";
        ((ObjectNode)prep.content.get("availability")).put("nextCursor", Base64.getUrlEncoder().withoutPadding()
            .encodeToString(cursor.getBytes(StandardCharsets.US_ASCII)));
        publish();
    }

    void publish() throws Exception {
        prep.state.payload.put("planSha256", SubmissionJson.sha(plan)); prep.repack(); prep.publish();
    }

    RecoverySourceTemporaryBaseline reader() {
        return new RecoverySourceTemporaryBaseline(new RecoveryReceiptKeys(List.of(prep.state.authority.key.pair.getPublic().getEncoded())));
    }
    ObjectNode line() { return (ObjectNode)plan.path("lines").get(0); }
    ObjectNode inventory() { return (ObjectNode)prep.content.get("inventory").get(0); }
}
