package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Nonempty native evidence under newly allocated migration-fixture IDs, with development-only signing. */
final class RecoveryUnsubmittedOccupancyDatabaseSeed {
    private RecoveryUnsubmittedOccupancyDatabaseSeed() { }

    static RecoveryUnsubmittedOccupancyFixture seed(JdbcTemplate jdbc, Map<String,String> seed, Path storage) throws Exception {
        var id = new RecoverySourceIdentity("TEMP", uuid(seed, "warehouseId"), uuid(seed, "mappedScopeId"), uuid(seed, "mappedSubmissionId"),
            uuid(seed, "deviceId"), uuid(seed, "operatorId"), uuid(seed, "authorizationId"), uuid(seed, "orderId"),
            uuid(seed, "deviceId"), uuid(seed, "mappedClientOrderId"), "1", 1, 1, 5, 1);
        var state = new RecoverySourceStateFixture(RecoverySourceFixture.original(id));
        String bale = seed.get("reservedBaleId");
        jdbc.update("UPDATE bale SET barcode=UPPER(barcode),dispatch_cycle=2 WHERE id=UUID_TO_BIN(?)", bale);
        jdbc.update("UPDATE bale_commitment SET dispatch_cycle=2 WHERE bale_id=UUID_TO_BIN(?)", bale);
        String barcode = jdbc.queryForObject("SELECT barcode FROM bale WHERE id=UUID_TO_BIN(?)", String.class, bale);
        ((ObjectNode)state.payload.at("/captures/0")).put("barcode", barcode);
        ((ObjectNode)state.payload.at("/scans/0")).put("barcode", barcode).put("baleId", bale);
        state.row().put("barcode", barcode).put("baleId", bale);
        var initial = new RecoveryInitialFixture(new RecoveryTemporaryActivationFixture(
            new RecoveryTemporaryBaselineFixture(new RecoveryPreparationFixture(state))).baseline.prep);
        initial.capture().put("rawInput", barcode);
        var evidence = new RecoveryOriginalEvidenceFixture(initial, ignored -> { });
        var database = new RecoveryDeviceRegistrationCapture.Database(UUID.fromString(jdbc.queryForObject("SELECT @@server_uuid", String.class)),
            jdbc.queryForObject("SELECT DATABASE()", String.class), "1");
        var f = new RecoveryUnsubmittedOccupancyFixture(evidence, uuid(seed, "mappedSetId"), database);
        jdbc.update("UPDATE submission SET status='ABORTED',receipt_text=? WHERE id=UUID_TO_BIN(?)",
            new String(initial.source().original("result"), StandardCharsets.UTF_8), id.submissionId().toString());
        evidence(jdbc, f, seed, storage); return f;
    }

    private static void evidence(JdbcTemplate jdbc, RecoveryUnsubmittedOccupancyFixture f, Map<String,String> seed, Path storage) throws Exception {
        var id = f.initial.expected(); var expected = RecoveryUnsubmittedEvidence.expected(f.evidence.request());
        Files.createDirectories(storage.resolve("unsubmitted")); byte[] stop = expected.stopProofBytes();
        Files.write(storage.resolve("unsubmitted/stop-proof.json"), stop, StandardOpenOption.CREATE_NEW);
        jdbc.update("UPDATE source_evidence_object SET bytes_sha256=?,byte_count=?,file_key=? WHERE id=UUID_TO_BIN(?)", sha256(stop), stop.length,
            "unsubmitted/stop-proof.json", seed.get("mappedObjectId"));
        jdbc.update("DELETE FROM source_evidence_item WHERE set_id=UUID_TO_BIN(?)", f.setId.toString());
        jdbc.update("UPDATE source_evidence_set SET manifest_bytes=?,set_sha256=?,capture_high_watermark=?,audit_through_seq=?,order_scan_high_watermark=?,item_count=? WHERE id=UUID_TO_BIN(?)",
            expected.manifestBytes(), expected.proof().setHash(), id.captureHighWatermark(), id.auditThroughSeq() + 1,
            id.orderScanHighWatermark(), expected.proof().itemCount(), f.setId.toString());
        for (var item : f.evidence.entries) item(jdbc, f, item, storage);
    }

    private static void item(JdbcTemplate jdbc, RecoveryUnsubmittedOccupancyFixture f, ObjectNode item, Path storage) throws Exception {
        var id = f.initial.expected(); UUID object = UUID.randomUUID(); String hash = text(item, "itemBytesSha256");
        String key = "unsubmitted/" + hash + ".json"; byte[] bytes = f.evidence.originals.get(hash);
        if (!Files.exists(storage.resolve(key))) Files.write(storage.resolve(key), bytes, StandardOpenOption.CREATE_NEW);
        jdbc.update("""
            INSERT INTO source_evidence_object(id,warehouse_id,scope_id,submission_id,work_round,kind,original_object_id,bytes_sha256,byte_count,file_key,origin_device_id,uploaded_by,received_at)
            VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,?,UUID_TO_BIN(?),?,?,?,UUID_TO_BIN(?),UUID_TO_BIN(?),UTC_TIMESTAMP(3))
            """, object.toString(), id.warehouseId().toString(), id.scopeId().toString(), id.submissionId().toString(), text(item, "objectKind"),
            text(item, "originalObjectId"), hash, bytes.length, key, id.deviceId().toString(), id.operatorId().toString());
        jdbc.update("""
            INSERT INTO source_evidence_item(set_id,warehouse_id,scope_id,submission_id,work_round,item_kind,original_record_id,object_id,original_device_seq,record_sha256,bale_id,original_cycle,capture_id,record_status)
            VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,?,UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,UUID_TO_BIN(?),?,UUID_TO_BIN(?),?)
            """, f.setId.toString(), id.warehouseId().toString(), id.scopeId().toString(), id.submissionId().toString(), text(item, "itemKind"),
            text(item, "originalRecordId"), object.toString(), item.path("originalDeviceSeq").asLong(), text(item, "recordSha256"),
            item.path("baleId").textValue(), item.path("originalCycle").isNull() ? null : item.path("originalCycle").longValue(),
            item.path("captureId").textValue(), text(item, "recordStatus"));
    }

    private static UUID uuid(Map<String,String> seed, String key) { return UUID.fromString(seed.get(key)); }
}
