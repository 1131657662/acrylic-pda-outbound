package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.PreparationArchive;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Synthetic facts for storage tests only; no original-device or HTTP proof is claimed. */
final class RetentionDatabaseFixture {
    final RetentionDatabaseProfile profile;
    final JdbcTemplate jdbc;
    final JdbcTemplate restore;
    final TransactionTemplate tx;
    final Map<String, String> seed;
    final List<Map<String, Object>> before;
    final UUID legacyDisposition;

    RetentionDatabaseFixture() throws Exception {
        this(false);
    }
    RetentionDatabaseFixture(boolean intake) throws Exception {
        this(intake,false);
    }
    RetentionDatabaseFixture(boolean intake,boolean unmapped) throws Exception {
        this(intake,unmapped,false);
    }
    RetentionDatabaseFixture(boolean intake,boolean unmapped,boolean android) throws Exception {
        profile = new RetentionDatabaseProfile(); jdbc = profile.connect(""); restore = profile.connect("_restore");
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); seed = E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        seedPreparation();
        jdbc.update("UPDATE submission SET status='ABORTED',receipt_text='synthetic-storage-abort' WHERE id=UUID_TO_BIN(?)",
            seed.get("mappedSubmissionId"));
        jdbc.update("UPDATE source_evidence_verification SET result='VERIFIED_COMPLETE_AT_STOP' WHERE id=UUID_TO_BIN(?)",
            seed.get("mappedVerificationId"));
        addScan("mapped", 3); addScan("mapped", 4);
        jdbc.update("UPDATE source_evidence_set SET item_count=4,capture_high_watermark=4 WHERE id=UUID_TO_BIN(?)", seed.get("mappedSetId"));
        if(intake)new TemporaryRoundIntakeSeed(this).prepare(unmapped,android);
        if(unmapped) {
            jdbc.update("UPDATE temporary_source_mapping SET canonical_order_id=NULL,mapping_version=0 WHERE scope_id=UUID_TO_BIN(?)",seed.get("mappedScopeId"));
            jdbc.update("UPDATE submission_scope SET canonical_order_id=NULL,mapping_version=0 WHERE id=UUID_TO_BIN(?)",seed.get("mappedScopeId"));
        }
        profile.migrate(jdbc, "59");
        tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        legacyDisposition = header("abort", false);
        item(legacyDisposition, newBale(), "QUARANTINE");
        before = historical(jdbc); profile.backupAndRestore(); profile.migrate(jdbc, "60"); profile.migrate(jdbc, "61"); profile.migrate(jdbc, "62"); profile.migrate(jdbc, "63"); profile.migrate(jdbc, "64");
        // This synthetic V34 seed contains SCAN rows but no CAPTURE rows. The trusted
        // continuation boundary is therefore supplied explicitly as the verified test fact.
        jdbc.update("UPDATE source_evidence_set SET order_scan_high_watermark=4 WHERE id=UUID_TO_BIN(?)", seed.get("mappedSetId"));
        jdbc.execute("CREATE TRIGGER retention_test_failure AFTER INSERT ON temporary_physical_disposition_item FOR EACH ROW "
            + "BEGIN IF NEW.action='RETAIN' AND @retention_test_fail=1 THEN SIGNAL SQLSTATE '45000' "
            + "SET MESSAGE_TEXT='retention test item failure'; END IF; END");
    }

    private void seedPreparation() {
        var manifest = SubmissionJson.object().put("schemaVersion", 1);
        var parts = manifest.putArray("parts");
        for (String name : List.of("inventory", "mapping", "policy", "rules", "availability")) {
            var part = PreparationArchive.pack(name, "{\"fixture\":\"continuation\",\"part\":\"" + name + "\"}", 1);
            var descriptor = parts.addObject()
                .put("name", part.descriptor().name())
                .put("compressedBytes", part.descriptor().compressedBytes())
                .put("bytes", part.descriptor().bytes())
                .put("compressedSha256", part.descriptor().compressedSha256())
                .put("sha256", part.descriptor().sha256())
                .put("rows", part.descriptor().rows());
            jdbc.update("INSERT INTO temporary_preparation_part(authorization_id,part_name,compressed) VALUES(?,?,?)",
                Ids.bytes(UUID.fromString(seed.get("authorizationId"))), name, part.compressed());
        }
        jdbc.update("UPDATE temporary_authorization SET manifest_text=?,manifest_sha256=? WHERE id=?",
            SubmissionJson.canonical(manifest), SubmissionJson.sha(manifest), Ids.bytes(UUID.fromString(seed.get("authorizationId"))));
    }

    private UUID newBale() {
        UUID bale = UUID.randomUUID();
        jdbc.update("INSERT INTO bale(id,barcode,warehouse_id,dispatch_cycle,created_at) VALUES(?,?,UUID_TO_BIN(?),1,UTC_TIMESTAMP(3))",
            Ids.bytes(bale), "B4-" + bale.toString().replace("-", "").substring(0, 8), seed.get("warehouseId"));
        return bale;
    }

    private UUID addScan(String mode, int sequence) {
        UUID bale = newBale();
        jdbc.update("""
            INSERT INTO source_evidence_item(set_id,warehouse_id,scope_id,submission_id,work_round,item_kind,
            original_record_id,object_id,original_device_seq,record_sha256,bale_id,original_cycle,capture_id,record_status)
            VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,'SCAN',?,UUID_TO_BIN(?),?,REPEAT('d',64),?,1,?,'ACTIVE')
            """, seed.get(mode + "SetId"), seed.get("warehouseId"), seed.get(mode + "ScopeId"), seed.get(mode + "SubmissionId"),
            Ids.bytes(UUID.randomUUID()), seed.get(mode + "ObjectId"), sequence, Ids.bytes(bale), Ids.bytes(UUID.randomUUID()));
        jdbc.update("""
            INSERT INTO bale_commitment(bale_id,order_id,submission_id,dispatch_cycle,state,created_at,
            source_scope_id,source_evidence_set_id,source_warehouse_id)
            VALUES(?,NULL,NULL,1,'SOURCE_RESERVED',UTC_TIMESTAMP(3),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))
            """, Ids.bytes(bale), seed.get(mode + "ScopeId"), seed.get(mode + "SetId"), seed.get("warehouseId"));
        return bale;
    }

    PhysicalDispositionVerification.Verified verified() {
        return new PhysicalDispositionVerification(jdbc).readForContinuation(id("warehouseId"), id("mappedScopeId"),
            id("mappedSubmissionId"), 1, id("mappedSetId"), id("mappedVerificationId"), jdbc.queryForObject(
                "SELECT set_sha256 FROM source_evidence_verification WHERE id=?", String.class, Ids.bytes(id("mappedVerificationId"))), 1, 1);
    }

    UUID apply(PhysicalDispositionVerification.Verified proof, List<String> actions) {
        var items = new ArrayList<PhysicalDispositionManifest.Item>();
        for (int i = 0; i < proof.activeBales().size(); i++)
            items.add(new PhysicalDispositionManifest.Item(proof.activeBales().get(i), actions.get(i)));
        return new PhysicalDispositionStore(jdbc).apply(proof, "b".repeat(64), 1, items, Instant.now());
    }

    UUID header(String mode, boolean close) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO temporary_physical_disposition(id,warehouse_id,scope_id,submission_id,work_round,evidence_set_id,
            evidence_verification_id,manifest_sha256,manifest_version,recorded_at,close_mode,close_reason)
            VALUES(?,UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,UUID_TO_BIN(?),UUID_TO_BIN(?),REPEAT('b',64),1,UTC_TIMESTAMP(3),?,?)
            """, Ids.bytes(id), seed.get("warehouseId"), seed.get(mode + "ScopeId"), seed.get(mode + "SubmissionId"),
            seed.get(mode + "SetId"), seed.get(mode + "VerificationId"), close, close ? "test close" : null);
        return id;
    }

    void item(UUID disposition, UUID bale, String action) {
        jdbc.update("INSERT INTO temporary_physical_disposition_item VALUES(?,?,1,'PHYSICAL_DISPOSED',?)",
            Ids.bytes(disposition), Ids.bytes(bale), action);
    }

    List<Map<String, Object>> stock() {
        return jdbc.queryForList("""
            SELECT HEX(bale_id) bale,HEX(order_id) target,HEX(submission_id) submission,dispatch_cycle,state,
            HEX(source_scope_id) scope,HEX(source_evidence_set_id) evidence,HEX(disposition_id) disposition
            FROM bale_commitment ORDER BY bale_id
            """);
    }

    static List<Map<String, Object>> historical(JdbcTemplate source) {
        return source.queryForList("""
            SELECT 'submission' kind,HEX(id) id,CONCAT(work_round,':',status,':',COALESCE(receipt_text,'')) content FROM submission
            UNION ALL SELECT 'receipt',HEX(id),HEX(original_response_bytes) FROM temporary_command_receipt
            UNION ALL SELECT 'set',HEX(id),HEX(manifest_bytes) FROM source_evidence_set
            UNION ALL SELECT 'disposition',HEX(id),manifest_sha256 FROM temporary_physical_disposition
            UNION ALL SELECT 'item',CONCAT(HEX(disposition_id),HEX(bale_id)),CONCAT(dispatch_cycle,':',action) FROM temporary_physical_disposition_item
            ORDER BY kind,id
            """);
    }

    static List<Map<String, Object>> dispositionHistory(JdbcTemplate source) {
        return source.queryForList("""
            SELECT HEX(disposition_id) disposition,HEX(scope_id) scope,HEX(submission_id) submission,
            work_round,HEX(bale_id) bale,dispatch_cycle,action
            FROM temporary_physical_disposition_history ORDER BY scope_id,work_round,bale_id
            """);
    }

    int headers() { return jdbc.queryForObject("SELECT COUNT(*) FROM temporary_physical_disposition", Integer.class); }
    UUID id(String key) { return UUID.fromString(seed.get(key)); }
    UUID uuid(byte[] bytes) { var buffer = java.nio.ByteBuffer.wrap(bytes); return new UUID(buffer.getLong(), buffer.getLong()); }
}
