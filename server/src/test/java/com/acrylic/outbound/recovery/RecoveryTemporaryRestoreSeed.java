package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Fresh synthetic source fixture. Credentials are persisted before the checkpoint, parts and ACK afterwards. */
final class RecoveryTemporaryRestoreSeed {
    static RecoveryTemporaryActivationFixture authorization(JdbcTemplate jdbc, Map<String,String> seed) throws Exception {
        var id = new RecoverySourceIdentity("TEMP", uuid(seed,"warehouseId"),uuid(seed,"mapping0ScopeId"),uuid(seed,"mapping0SubmissionId"),
            uuid(seed,"deviceId"),uuid(seed,"operatorId"),uuid(seed,"authorizationId"),null,uuid(seed,"deviceId"),uuid(seed,"mapping0ClientOrderId"),
            "1",1,7,12,9);
        var f = new RecoveryTemporaryActivationFixture(new RecoveryTemporaryBaselineFixture(
            new RecoveryPreparationFixture(new RecoverySourceStateFixture(RecoverySourceFixture.original(id)))));
        var prep = f.baseline.prep;
        var baseline = jdbc.queryForMap("SELECT BIN_TO_UUID(inventory_snapshot_id) snapshot,BIN_TO_UUID(policy_id) policy FROM temporary_authorization WHERE id=?",
            Ids.bytes(id.qualificationId()));
        prep.manifest.put("inventorySnapshotId", baseline.get("snapshot").toString());
        prep.state.payload.put("inventorySnapshotId", baseline.get("snapshot").toString());
        ((ObjectNode)prep.state.payload.path("captures").get(0)).put("snapshotId", baseline.get("snapshot").toString());
        f.baseline.policy.put("id", baseline.get("policy").toString());
        alignInventory(jdbc, f, seed); alignMapping(jdbc,f); f.baseline.publish();
        f.command.put("manifestSha256", text(prep.receipt,"manifestSha256"));
        f.proof.put("manifestSha256", text(prep.receipt,"manifestSha256")).put("commandSha256", f.digest()); f.publish();
        jdbc.update("UPDATE temporary_authorization SET bundle_id=UUID_TO_BIN(?),user_auth_version=3,device_version=7,issued_at=?,expires_at=?,"
            + "signed_token=?,manifest_text=?,manifest_sha256=?,signed_manifest=?,key_id=? WHERE id=?",
            text(prep.receipt,"bundleId"),Timestamp.from(RecoveryPreparationBinding.time(prep.manifest,"issuedAt")),
            Timestamp.from(RecoveryPreparationBinding.time(prep.manifest,"expiresAt")),text(prep.receipt,"offlineToken"),text(prep.receipt,"manifest"),
            text(prep.receipt,"manifestSha256"),text(prep.receipt,"signedManifest"),prep.state.authority.key.kid,Ids.bytes(id.qualificationId()));
        return f;
    }

    private static void alignInventory(JdbcTemplate jdbc, RecoveryTemporaryActivationFixture f, Map<String,String> seed) {
        var row = f.baseline.inventory();
        var scan = (ObjectNode)f.baseline.prep.state.payload.path("scans").get(0);
        scan.put("baleId",seed.get("baleId"));
        ((ObjectNode)f.baseline.prep.content.get("availability").path("items").get(0)).put("baleId",seed.get("baleId"));
        jdbc.update("UPDATE bale SET barcode=? WHERE id=UUID_TO_BIN(?)",text(row,"barcode"),seed.get("baleId"));
        jdbc.update("UPDATE inventory_snapshot_bale SET delivery_code=?,model_snapshot=?,name_snapshot=?,raw_weight=?,weight_g=? WHERE bale_id=UUID_TO_BIN(?)",
            text(row,"deliveryCode"),text(row,"model"),text(row,"name"),text(row,"rawWeight"),text(row,"weightGrams"),seed.get("baleId"));
    }

    private static void alignMapping(JdbcTemplate jdbc,RecoveryTemporaryActivationFixture f) {
        var source=f.baseline.prep.state.authority.source;
        var row=jdbc.queryForMap("SELECT BIN_TO_UUID(id) mappingId,BIN_TO_UUID(product_id) productId FROM delivery_mapping WHERE warehouse_id=?",
            Ids.bytes(source.identity.warehouseId()));
        f.baseline.mapping.put("id",row.get("mappingId").toString()).put("productId",row.get("productId").toString());
        f.baseline.line().put("productId",row.get("productId").toString());
        jdbc.update("UPDATE delivery_mapping SET delivery_code='012' WHERE id=UUID_TO_BIN(?)",row.get("mappingId"));
        jdbc.update("UPDATE product SET model='MODEL',name='Product' WHERE id=UUID_TO_BIN(?)",row.get("productId"));
    }

    static void complete(JdbcTemplate jdbc, RecoveryTemporaryActivationFixture f) {
        var prep = f.baseline.prep; byte[] id = Ids.bytes(prep.state.authority.source.identity.qualificationId());
        for (String name : List.of("inventory","mapping","policy","rules","availability"))
            jdbc.update("INSERT INTO temporary_preparation_part VALUES(?,?,?)",id,name,Base64.getDecoder().decode(text(prep.origin.path("parts"),name)));
        jdbc.update("INSERT INTO temporary_preparation_ack VALUES(?,?,?,?,?)",id,f.digest(),
            Timestamp.from(Instant.parse(text(f.receipt,"activatedAt"))),Timestamp.from(Instant.parse(text(f.receipt,"acknowledgedAt"))),text(f.receipt,"signedAck"));
    }
    private static UUID uuid(Map<String,String> seed,String key) { return UUID.fromString(seed.get(key)); }
}
