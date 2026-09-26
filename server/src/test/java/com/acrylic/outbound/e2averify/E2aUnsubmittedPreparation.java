package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.*;
import com.acrylic.outbound.temporary.*;
import com.acrylic.outbound.policies.PolicyStore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import org.springframework.context.ConfigurableApplicationContext;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Synthetic original credentials; issuer signatures and retained preparation parts are real. */
final class E2aUnsubmittedPreparation {
    final E2aCrossSourceFixture f;
    final UUID formalAuthorization = UUID.randomUUID(), temporaryAuthorization = UUID.randomUUID();
    final String snapshot;
    final Instant issued = Instant.parse("2026-09-24T00:00:00Z"), expires = issued.plusSeconds(3600);
    final ObjectNode inventoryRow, available;
    private final OfflineSigner signer;
    private final JsonText json;

    E2aUnsubmittedPreparation(E2aCrossSourceFixture f, ConfigurableApplicationContext context) {
        this.f = f; signer = context.getBean(OfflineSigner.class); json = context.getBean(JsonText.class);
        snapshot = f.jdbc.queryForObject("SELECT BIN_TO_UUID(inventory_snapshot_id) FROM order_assignment WHERE id=?", String.class, f.id("assignmentId"));
        var values = f.jdbc.queryForMap("SELECT barcode FROM bale WHERE id=?", Ids.bytes(f.priorBale));
        inventoryRow = object().put("barcode", values.get("barcode").toString().toUpperCase(Locale.ROOT))
            .put("deliveryCode", "E2A").put("model", "E2A-MODEL").put("name", "E2a Product").put("rawWeight", "1000").put("weightGrams", "1000");
        available = object().put("barcode", text(inventoryRow, "barcode")).put("baleId", f.priorBale.toString())
            .put("dispatchCycle", "1").put("available", true);
        formal(context); temporary(context);
    }

    private List<PreparationArchive> parts(boolean temp) {
        var rows = object().putArray("rows").add(inventoryRow);
        var availability = object().putArray("rows").add(available);
        ObjectNode state = object(); state.set("items", availability);
        if (temp) return List.of(PreparationArchive.pack("inventory", json.encode(rows), 1),
            PreparationArchive.pack("mapping", "[]", 0), PreparationArchive.pack("policy", "{}", 1),
            PreparationArchive.pack("rules", "{}", 1), PreparationArchive.pack("availability", json.encode(state), 1));
        return List.of(PreparationArchive.pack("plan", "{}", 1), PreparationArchive.pack("inventory", json.encode(rows), 1),
            PreparationArchive.pack("availability", json.encode(availability), 1));
    }

    private void formal(ConfigurableApplicationContext context) {
        var parts = parts(false); String bundle = UUID.randomUUID().toString();
        f.jdbc.update("UPDATE order_assignment SET plan_sha256=?,inventory_sha256=? WHERE id=?",
            parts.get(0).descriptor().sha256(), parts.get(1).descriptor().sha256(), f.id("assignmentId"));
        var claims = object().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-offline").put("jti", formalAuthorization.toString())
            .put("sub", f.seed.get("operatorId")).put("deviceId", f.seed.get("deviceId")).put("warehouseId", f.seed.get("warehouseId"))
            .put("assignmentId", f.seed.get("assignmentId")).put("orderId", f.seed.get("orderId")).put("revision", 1)
            .put("assignmentEpoch", "1").put("serverRecoveryEpoch", "1").put("inventorySnapshotId", snapshot)
            .put("scope", "pda:scan").put("planSha256", parts.get(0).descriptor().sha256())
            .put("inventorySha256", parts.get(1).descriptor().sha256()).put("iat", issued.getEpochSecond()).put("exp", expires.getEpochSecond());
        var signed = signer.sign(claims);
        f.jdbc.update("""
            INSERT INTO offline_authorization(id,assignment_id,authorization_sequence,user_id,device_id,issued_at,expires_at,key_id,signed_token)
            VALUES(?,?,0,?,?,?,?,?,?)
            """, Ids.bytes(formalAuthorization), f.id("assignmentId"), f.id("operatorId"), f.id("deviceId"),
            java.sql.Timestamp.from(issued), java.sql.Timestamp.from(expires), signed.keyId(), signed.token());
        var manifest = new PreparationService.Manifest(1, bundle, f.seed.get("assignmentId"), f.seed.get("orderId"),
            f.seed.get("deviceId"), f.seed.get("operatorId"), "1", "1", snapshot, 1, issued.toString(), expires.toString(),
            parts.stream().map(PreparationArchive::descriptor).toList());
        String raw = json.encode(manifest), sha = com.acrylic.outbound.devices.DeviceCrypto.sha(raw);
        var proof = signer.sign(Map.of("iss", "acrylic-outbound", "aud", "acrylic-pda-preparation", "assignmentId", f.seed.get("assignmentId"), "manifestSha256", sha));
        context.getBean(PreparationStore.class).insert(f.uuid("assignmentId"), new PreparationStore.Receipt(bundle, raw, sha, proof.token()), parts);
    }

    private void temporary(ConfigurableApplicationContext context) {
        var principal = new PdaSessions.Principal(UUID.randomUUID(), f.uuid("operatorId"), "synthetic", f.uuid("deviceId"), f.uuid("warehouseId"), "1", "1");
        var policy = context.getBean(PolicyStore.class).current(f.uuid("warehouseId"));
        var baseline = new TemporaryBaseline.View(f.seed.get("warehouseId"), snapshot, "1", "1", 1, "", policy);
        var work = context.getBean(TemporaryIssuer.class).issue(new TemporaryIssuer.Binding(temporaryAuthorization, UUID.randomUUID(), 2, "1", principal), baseline, parts(true));
        context.getBean(TemporaryStore.class).insert(work);
    }
}
