package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.pda.PreparationArchive;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Builds coherent synthetic stopped facts before immutable migrations; never edits an existing attempt. */
final class TemporaryRoundIntakeSeed {
    private final RetentionDatabaseFixture f;
    TemporaryRoundIntakeSeed(RetentionDatabaseFixture f) { this.f=f; }
    void prepare(boolean local) throws java.io.IOException {
        prepare(local,false);
    }
    void prepare(boolean local,boolean android) throws java.io.IOException {
        baseline();var body=new TemporaryRoundIntakeBody(f).original();
        if(android)new TemporaryAndroidIntakeSeed(f).prepare(body);
        for(var scan:body.path("scans"))f.jdbc.update("UPDATE source_evidence_item SET record_sha256=? WHERE set_id=UUID_TO_BIN(?) AND capture_id=UUID_TO_BIN(?)",
            sha(scan),f.seed.get("mappedSetId"),text(scan,"captureId"));
        byte[] bytes=canonical(body).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash=new SubmissionFiles(f.profile.attempt.resolve("storage").toString()).store(bytes);
        new TemporaryRoundIntakeArchive(f).store(body,local);
        f.seed.put("intakePredecessorHash",hash);if(!android)parts();
    }
    private void baseline() {
        var jdbc=f.jdbc;String warehouse=f.seed.get("warehouseId");
        var base=jdbc.queryForMap("SELECT BIN_TO_UUID(inventory_snapshot_id) snapshot,BIN_TO_UUID(policy_id) policy FROM temporary_authorization WHERE id=UUID_TO_BIN(?)",f.seed.get("authorizationId"));
        f.seed.put("intakeSnapshot",(String)base.get("snapshot"));
        var mapping=jdbc.queryForMap("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(product_id) product FROM delivery_mapping WHERE warehouse_id=UUID_TO_BIN(?)",warehouse);
        f.seed.put("intakeMapping",(String)mapping.get("id"));f.seed.put("intakeProduct",(String)mapping.get("product"));
        jdbc.update("UPDATE delivery_mapping SET delivery_code='001' WHERE id=UUID_TO_BIN(?)",f.seed.get("intakeMapping"));
        jdbc.update("UPDATE inventory_snapshot_bale SET delivery_code='001' WHERE snapshot_id=UUID_TO_BIN(?)",f.seed.get("intakeSnapshot"));
        jdbc.update("UPDATE outbound_order SET source='PDA_TEMP',status='REVIEW_REQUIRED' WHERE id=UUID_TO_BIN(?)",f.seed.get("orderId"));
        jdbc.update("UPDATE order_revision SET vehicle_no='SYNTHETIC',reason='synthetic intake',planned_total=4 WHERE order_id=UUID_TO_BIN(?)",f.seed.get("orderId"));
        jdbc.update("UPDATE order_line SET delivery_code='001',planned_count=4 WHERE revision_id IN (SELECT id FROM order_revision WHERE order_id=UUID_TO_BIN(?))",f.seed.get("orderId"));
        jdbc.update("UPDATE device SET temporary_order_enabled=1 WHERE id=UUID_TO_BIN(?)",f.seed.get("deviceId"));
        f.seed.put("intakeTime",jdbc.queryForObject("SELECT issued_at FROM temporary_authorization WHERE id=UUID_TO_BIN(?)",java.sql.Timestamp.class,f.seed.get("authorizationId")).toInstant().toString());
        var bales=jdbc.queryForList("SELECT BIN_TO_UUID(bale_id) FROM source_evidence_item WHERE set_id=UUID_TO_BIN(?) AND item_kind='SCAN' ORDER BY original_device_seq",String.class,f.seed.get("mappedSetId"));
        for(int i=0;i<bales.size();i++)bale(bales.get(i),i+2);
        String added=UUID.randomUUID().toString();f.seed.put("intakeNewBale",added);
        jdbc.update("INSERT INTO bale(id,barcode,warehouse_id,dispatch_cycle,created_at) VALUES(UUID_TO_BIN(?),'00000000005',UUID_TO_BIN(?),1,UTC_TIMESTAMP(3))",added,warehouse);
        bale(added,5);
    }
    private void bale(String id,int sequence) {
        f.jdbc.update("UPDATE bale SET barcode=? WHERE id=UUID_TO_BIN(?)",String.format("%011d",sequence),id);
        f.jdbc.update("INSERT IGNORE INTO inventory_snapshot_bale VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'001',UUID_TO_BIN(?),'E2A-MODEL','E2a Product',1000,'1000')",
            f.seed.get("intakeSnapshot"),id,f.seed.get("intakeProduct"));
        f.jdbc.update("INSERT IGNORE INTO inventory_position(bale_id,latest_snapshot_id,last_seen_snapshot_id,upstream_present,book_status,row_version) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,'NOT_DEDUCTED',1)",
            id,f.seed.get("intakeSnapshot"),f.seed.get("intakeSnapshot"));
    }
    private void parts() {
        var manifest=object().put("schemaVersion",1).put("inventorySnapshotId",f.seed.get("intakeSnapshot"))
            .put("mappingRevision","1").put("policyVersion","1");var descriptors=manifest.putArray("parts");
        for(var part:new TemporaryRoundIntakeParts(f).build()) {
            var d=part.descriptor();String name=d.name();
            descriptors.addObject().put("name",d.name()).put("compressedBytes",d.compressedBytes()).put("bytes",d.bytes())
                .put("compressedSha256",d.compressedSha256()).put("sha256",d.sha256()).put("rows",d.rows());
            f.jdbc.update("UPDATE temporary_preparation_part SET compressed=? WHERE authorization_id=UUID_TO_BIN(?) AND part_name=?",part.compressed(),f.seed.get("authorizationId"),name);
        }
        f.jdbc.update("UPDATE temporary_authorization SET manifest_text=?,manifest_sha256=? WHERE id=UUID_TO_BIN(?)",canonical(manifest),sha(manifest),f.seed.get("authorizationId"));
    }
}
