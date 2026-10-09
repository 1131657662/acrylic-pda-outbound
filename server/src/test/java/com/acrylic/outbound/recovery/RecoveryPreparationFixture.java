package com.acrylic.outbound.recovery;

import com.acrylic.outbound.pda.PreparationArchive;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/** Native preparation encodings with synthetic archive state and development signatures. */
final class RecoveryPreparationFixture {
    final RecoverySourceStateFixture state;
    final ObjectNode manifest = SubmissionJson.object();
    final ObjectNode origin = SubmissionJson.object();
    final ObjectNode receipt = SubmissionJson.object();
    final Map<String, JsonNode> content = new HashMap<>();
    final boolean temporary;

    RecoveryPreparationFixture(String kind) throws Exception {
        temporary = kind.equals("TEMP"); state = new RecoverySourceStateFixture(kind);
        var scan = (ObjectNode)state.payload.path("scans").get(0);
        scan.put("deliveryCode", "012").put("model", "MODEL").put("name", "Product").put("rawWeight", "1.234").put("weightGrams", "1234");
        state.payload.put("expectedWeightGrams", "1234").put("expectedCount", "1");
        var inventory = SubmissionJson.object().putArray("rows"); var row = inventory.addObject().put("barcode", scan.path("barcode").asText());
        for (String field : List.of("deliveryCode", "model", "name", "rawWeight", "weightGrams")) row.set(field, scan.path(field));
        content.put("inventory", inventory);
        var availability = SubmissionJson.object().putArray("rows");
        var available = availability.addObject().put("barcode", scan.path("barcode").asText()).put("baleId", scan.path("baleId").asText())
            .put("dispatchCycle", "2");
        if (temporary) available.put("status", "AVAILABLE").put("rowVersion", "1"); else available.put("available", true);
        var id = state.authority.source.identity;
        var tempAvailability = SubmissionJson.object().put("warehouseId", id.warehouseId().toString())
            .put("recoveryEpoch", id.sourceRecoveryEpoch()).put("streamType", "BARCODE").put("highWatermark", "0").put("nextCursor", "synthetic");
        tempAvailability.set("items", availability); content.put("availability", temporary ? tempAvailability : availability);
        UUID bundle = UUID.randomUUID(); var claims = state.authority.claims;
        if (temporary) temporary(bundle); else formal(bundle);
        state.payload.put("inventorySnapshotId", manifest.path("inventorySnapshotId").asText());
        if (!temporary) state.payload.put("orderRevision", 1);
        repack(); publish();
    }

    private void formal(UUID bundle) {
        var id = state.authority.source.identity; var claims = state.authority.claims;
        manifest.put("schemaVersion", 1).put("bundleId", bundle.toString()).put("assignmentId", id.qualificationId().toString())
            .put("orderId", id.canonicalOrderId().toString()).put("deviceId", id.deviceId().toString()).put("operatorId", id.operatorId().toString())
            .put("assignmentEpoch", "1").put("serverRecoveryEpoch", id.sourceRecoveryEpoch())
            .put("inventorySnapshotId", claims.path("inventorySnapshotId").asText()).put("revision", 1)
            .put("stateCapturedAt", "2026-09-20T00:01:00Z").put("expiresAt", "2026-09-21T00:00:00Z");
        receipt.put("bundleId", bundle.toString());
        var plan = SubmissionJson.object().put("serverRecoveryEpoch", id.sourceRecoveryEpoch()); var order = plan.putObject("order");
        order.putObject("order").put("id", id.canonicalOrderId().toString());
        order.putObject("plan").put("inventorySnapshotId", claims.path("inventorySnapshotId").asText()).put("revision", 1);
        order.putArray("lines").addObject().put("deliveryCode", "012").put("plannedCount", 1);
        content.put("plan", plan);
    }

    private void temporary(UUID bundle) {
        var id = state.authority.source.identity;
        ((ObjectNode)state.authority.claims.path("identity")).put("bundleId", bundle.toString());
        manifest.put("schemaVersion", 1).put("purpose", "TEMP_WORK").put("inventorySnapshotId", UUID.randomUUID().toString())
            .put("inventoryVersion", "1").put("mappingRevision", "1").put("policyVersion", "1").put("barcodeRuleVersion", "1")
            .put("issuedAt", "2026-09-20T00:00:00Z").put("expiresAt", "2026-09-21T00:00:00Z");
        manifest.set("identity", state.authority.claims.path("identity").deepCopy());
        receipt.put("authorizationId", id.qualificationId().toString()).put("bundleId", bundle.toString());
        content.put("mapping", SubmissionJson.object().putArray("rows"));
        content.put("policy", SubmissionJson.object()); content.put("rules", SubmissionJson.object());
    }

    void repack() {
        var descriptors = manifest.putArray("parts"); var originals = origin.putObject("parts");
        for (String name : content.keySet().stream().sorted().toList()) {
            var raw = content.get(name); int rows = raw.isArray() ? raw.size() : name.equals("availability") ? raw.path("items").size() : 1;
            var packed = PreparationArchive.pack(name, raw.toString(), rows); var descriptor = packed.descriptor();
            descriptors.addObject().put("name", name).put("compressedBytes", descriptor.compressedBytes()).put("bytes", descriptor.bytes())
                .put("compressedSha256", descriptor.compressedSha256()).put("sha256", descriptor.sha256()).put("rows", descriptor.rows());
            originals.put(name, Base64.getEncoder().encodeToString(packed.compressed()));
            if (!temporary && name.equals("plan")) state.authority.claims.put("planSha256", descriptor.sha256());
            if (!temporary && name.equals("inventory")) state.authority.claims.put("inventorySha256", descriptor.sha256());
        }
    }

    void publish() throws Exception {
        String digest = PreparationArchive.sha(manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var proof = SubmissionJson.object().put("iss", "acrylic-outbound").put("aud", temporary ? "acrylic-pda-temporary-preparation" : "acrylic-pda-preparation")
            .put("manifestSha256", digest).put("exp", state.authority.claims.path("exp").longValue());
        if (temporary) {
            state.authority.claims.put("manifestSha256", digest);
            proof.put("authorizationId", state.authority.source.identity.qualificationId().toString()).put("bundleId", receipt.path("bundleId").asText())
                .put("iat", state.authority.claims.path("iat").longValue());
        } else proof.put("assignmentId", state.authority.source.identity.qualificationId().toString());
        var header = SubmissionJson.object().put("alg", "RS256").put("typ", "JWT").put("kid", state.authority.key.kid);
        String token = state.authority.key.token(header, state.authority.claims); origin.put("offlineToken", token);
        receipt.put("manifest", manifest.toString()).put("manifestSha256", digest).put("signedManifest", state.authority.key.token(header, proof));
        if (temporary) receipt.put("offlineToken", token);
        origin.set("preparationReceipt", receipt); state.publish(); write();
    }

    void write() throws Exception { state.object("origin", origin); }
    RecoverySourcePreparation reader() { return new RecoverySourcePreparation(new RecoveryReceiptKeys(List.of(state.authority.key.pair.getPublic().getEncoded()))); }
    RecoveryPinnedSource read() { return state.read(); }
    ObjectNode descriptor(String name) {
        for (var row : manifest.path("parts")) if (name.equals(row.path("name").asText())) return (ObjectNode)row;
        throw new IllegalArgumentException(name);
    }
}
