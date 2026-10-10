package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Retained snapshot rows and policy must agree with the signed baseline; no current stock or permission is inferred. */
final class RecoveryTemporaryRestoreBaseline {
    private final JdbcTemplate jdbc;
    RecoveryTemporaryRestoreBaseline(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void requireMatches(RecoveryTemporaryRestoreMaterial material) {
        var manifest = material.manifest(); byte[] snapshot = Ids.bytes(uuid(manifest, "inventorySnapshotId"));
        var versions = jdbc.query("SELECT version FROM inventory_snapshot WHERE id=? AND warehouse_id=? FOR UPDATE",
            (rs, n) -> rs.getString(1), snapshot, Ids.bytes(material.identity().warehouseId()));
        require(versions.size() == 1 && versions.getFirst().equals(text(manifest, "inventoryVersion")), "RECOVERY_TEMP_RESTORE_BASELINE_CONFLICT");
        inventory(material, snapshot); policy(material); mappings(material);
    }

    private void inventory(RecoveryTemporaryRestoreMaterial material, byte[] snapshot) {
        var expected = new TreeMap<String, JsonNode>();
        for (var row : material.content().get("inventory")) expected.put(text(row, "barcode"), row);
        var bales = new HashMap<String, UUID>();
        for (var row : material.content().get("availability").path("items")) bales.put(text(row, "barcode"), uuid(row, "baleId"));
        var actual = new TreeMap<String, JsonNode>();
        jdbc.query("""
            SELECT b.barcode,s.delivery_code,s.model_snapshot,s.name_snapshot,s.raw_weight,s.weight_g,
             BIN_TO_UUID(b.id) baleId,BIN_TO_UUID(b.warehouse_id) warehouseId
            FROM inventory_snapshot_bale s JOIN bale b ON b.id=s.bale_id
            WHERE s.snapshot_id=? FOR UPDATE
            """, rs -> {
                var row = SubmissionJson.object().put("barcode", rs.getString(1)).put("deliveryCode", rs.getString(2))
                    .put("model", rs.getString(3)).put("name", rs.getString(4)).put("rawWeight", rs.getString(5)).put("weightGrams", rs.getString(6));
                require(UUID.fromString(rs.getString(7)).equals(bales.get(rs.getString(1)))
                    && UUID.fromString(rs.getString(8)).equals(material.identity().warehouseId()), "RECOVERY_TEMP_RESTORE_BALE_CONFLICT");
                actual.put(rs.getString(1), row);
            }, snapshot);
        require(actual.equals(expected), "RECOVERY_TEMP_RESTORE_INVENTORY_CONFLICT");
    }

    private void mappings(RecoveryTemporaryRestoreMaterial material) {
        for (var expected : material.content().get("mapping")) {
            var actual = jdbc.query("""
                SELECT m.delivery_code,m.version,BIN_TO_UUID(m.product_id),p.model,p.name,m.enabled
                FROM delivery_mapping m JOIN product p ON p.id=m.product_id
                WHERE m.id=? AND m.warehouse_id=? FOR UPDATE
                """, (rs, n) -> SubmissionJson.object().put("deliveryCode", rs.getString(1)).put("version", rs.getString(2))
                    .put("productId", rs.getString(3)).put("model", rs.getString(4)).put("name", rs.getString(5)).put("enabled", rs.getBoolean(6)),
                Ids.bytes(uuid(expected, "id")), Ids.bytes(material.identity().warehouseId()));
            require(actual.size() == 1, "RECOVERY_TEMP_RESTORE_MAPPING_CONFLICT");
            for (String field : "deliveryCode version productId model name enabled".split(" "))
                require(actual.getFirst().path(field).equals(expected.path(field)), "RECOVERY_TEMP_RESTORE_MAPPING_CONFLICT");
        }
    }

    private void policy(RecoveryTemporaryRestoreMaterial material) {
        var expected = material.content().get("policy");
        var rows = jdbc.query("""
            SELECT version,auto_deduct_inventory,schema_version,offline_hours,short_shipment_policy,concurrency_mode,scan_mode
            FROM policy_version WHERE id=? AND warehouse_id=? FOR UPDATE
            """, (rs, n) -> SubmissionJson.object().put("version", rs.getString(1)).put("autoDeductInventory", rs.getBoolean(2))
                .put("schemaVersion", rs.getInt(3)).put("offlineHours", rs.getInt(4)).put("shortShipmentPolicy", rs.getString(5))
                .put("concurrencyMode", rs.getString(6)).put("scanMode", rs.getString(7)),
            Ids.bytes(uuid(expected, "id")), Ids.bytes(material.identity().warehouseId()));
        require(rows.size() == 1, "RECOVERY_TEMP_RESTORE_POLICY_CONFLICT");
        for (String field : "version autoDeductInventory schemaVersion offlineHours shortShipmentPolicy concurrencyMode scanMode".split(" "))
            require(rows.getFirst().path(field).equals(expected.path(field)), "RECOVERY_TEMP_RESTORE_POLICY_CONFLICT");
    }
}
