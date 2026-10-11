package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakePageObjects;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class E2aCrossSourceFixture {
    final JdbcTemplate jdbc;
    final SubmissionFiles files;
    final Map<String, String> seed;
    final UUID scope = UUID.randomUUID(), submission = UUID.randomUUID(), client = UUID.randomUUID();
    final UUID priorScope = UUID.randomUUID(), priorSubmission = UUID.randomUUID();
    final UUID ownBale = UUID.randomUUID(), priorBale = UUID.randomUUID();
    final UUID ownOrder = UUID.randomUUID();
    final ObjectNode earlier, current, priorScan, ownScan;

    E2aCrossSourceFixture(JdbcTemplate jdbc, SubmissionFiles files, Map<String, String> seed) {
        this.jdbc = jdbc; this.files = files; this.seed = seed;
        earlier = capture(1); current = capture(2);
        priorScan = scan(earlier, priorBale, 1); ownScan = scan(current, ownBale, 2);
    }

    void seed() {
        source(); formalSource(); stock(ownBale); stock(priorBale);
        savePayload(submission, current, ownScan);
        savePayload(priorSubmission, earlier, priorScan);
        storeCapture(submission, current); storeCapture(priorSubmission, earlier);
    }

    private void source() {
        temporaryOrder();
        jdbc.update("INSERT INTO temporary_work_origin VALUES(?,?,?)", id("deviceId"), Ids.bytes(client), id("authorizationId"));
        jdbc.update("""
            INSERT INTO submission_scope(id,warehouse_id,creator_device_id,client_order_id,work_round,canonical_order_id,mapping_version)
            VALUES(?,?,?,?,1,?,1)
            """, Ids.bytes(scope), id("warehouseId"), id("deviceId"), Ids.bytes(client), Ids.bytes(ownOrder));
        jdbc.update("""
            INSERT INTO temporary_source_mapping(id,warehouse_id,scope_id,mapping_version,canonical_order_id,origin,observed_at)
            VALUES(?,?,?,1,?,'SOURCE_REGISTERED',UTC_TIMESTAMP(3))
            """, Ids.bytes(UUID.randomUUID()), id("warehouseId"), Ids.bytes(scope), Ids.bytes(ownOrder));
        jdbc.update("INSERT INTO temporary_decision_state VALUES(?,0,'SOURCE_REGISTERED',UTC_TIMESTAMP(3))", Ids.bytes(scope));
        jdbc.update("""
            INSERT INTO submission(id,scope_id,temporary_authorization_id,freeze_device_id,operator_id,
              work_round,source_recovery_epoch,status,created_at,updated_at)
            VALUES(?,?,?,?,?,1,1,'REVIEW_REQUIRED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """, Ids.bytes(submission), Ids.bytes(scope), id("authorizationId"), id("deviceId"), id("operatorId"));
        jdbc.update("UPDATE submission_scope SET current_submission_id=? WHERE id=?", Ids.bytes(submission), Ids.bytes(scope));
        jdbc.update("""
            INSERT INTO order_submission_slot(order_id,scope_id,current_submission_id,work_round,phase)
            VALUES(?,?,?,1,'FROZEN')
            """, Ids.bytes(ownOrder), Ids.bytes(scope), Ids.bytes(submission));
    }

    private void temporaryOrder() {
        jdbc.update("""
            INSERT INTO outbound_order(id,warehouse_id,order_no,source,status,created_by,created_at,updated_at)
            VALUES(?,?,?,'PDA_TEMP','REVIEW_REQUIRED',?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """, Ids.bytes(ownOrder), id("warehouseId"), "E2A-" + ownOrder, id("operatorId"));
    }

    private void formalSource() {
        jdbc.update("INSERT INTO submission_scope(id,warehouse_id,source_order_id,work_round) VALUES(?,?,?,1)",
            Ids.bytes(priorScope), id("warehouseId"), id("orderId"));
        jdbc.update("""
            INSERT INTO submission(id,scope_id,assignment_id,freeze_device_id,operator_id,
              work_round,source_recovery_epoch,status,created_at,updated_at)
            VALUES(?,?,?,?,?,1,1,'REVIEW_REQUIRED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """, Ids.bytes(priorSubmission), Ids.bytes(priorScope), id("assignmentId"), id("deviceId"), id("operatorId"));
        jdbc.update("UPDATE submission_scope SET current_submission_id=? WHERE id=?", Ids.bytes(priorSubmission), Ids.bytes(priorScope));
    }

    private void stock(UUID bale) {
        jdbc.update("INSERT INTO bale(id,barcode,warehouse_id,dispatch_cycle,created_at) VALUES(?,?,?,1,UTC_TIMESTAMP(3))",
            Ids.bytes(bale), "C25" + bale.toString().replace("-", "").substring(0, 8), id("warehouseId"));
        jdbc.update("""
            INSERT INTO inventory_snapshot_bale
              (snapshot_id,bale_id,delivery_code,product_id,model_snapshot,name_snapshot,weight_g,raw_weight)
            SELECT snapshot_id,?,delivery_code,product_id,model_snapshot,name_snapshot,weight_g,raw_weight
            FROM inventory_snapshot_bale WHERE bale_id=?
            """, Ids.bytes(bale), id("baleId"));
        jdbc.update("""
            INSERT INTO inventory_position(bale_id,latest_snapshot_id,last_seen_snapshot_id,upstream_present,book_status,row_version)
            SELECT ?,latest_snapshot_id,last_seen_snapshot_id,1,'NOT_DEDUCTED',1 FROM inventory_position WHERE bale_id=?
            """, Ids.bytes(bale), id("baleId"));
    }

    private void savePayload(UUID target, ObjectNode capture, ObjectNode scan) {
        var body = SubmissionJson.object(); body.putArray("captures").add(capture); body.putArray("scans").add(scan);
        byte[] bytes = bytes(body); String hash = files.store(bytes);
        jdbc.update("""
            INSERT INTO submission_payload VALUES(?,2,?,?,?,1,1000,?,UTC_TIMESTAMP(3))
            """, Ids.bytes(target), hash, "submissions/" + hash + ".json", bytes.length,
            SubmissionJson.number(capture, "deviceCaptureSeq"));
    }

    private void storeCapture(UUID target, ObjectNode capture) {
        jdbc.update("INSERT INTO submitted_capture(id,submission_id,order_id,origin_device_id,device_capture_seq,order_scan_seq,record_sha256) VALUES(?,?,?,?,?,?,?)", Ids.bytes(SubmissionJson.uuid(capture, "id")),
            Ids.bytes(target), target.equals(submission) ? Ids.bytes(ownOrder) : id("orderId"),
            id("deviceId"), SubmissionJson.number(capture, "deviceCaptureSeq"),
            SubmissionJson.number(capture, "orderScanSeq"), SubmissionJson.sha(capture));
    }

    List<E2aPageItem> items() {
        return List.of(item("CAPTURE", earlier), item("SCAN", priorScan), item("CAPTURE", current), item("SCAN", ownScan));
    }

    SourceEvidenceIntakePageObjects.Binding binding(UUID open) {
        return new SourceEvidenceIntakePageObjects.Binding(uuid("warehouseId"), scope, submission, 1,
            uuid("deviceId"), uuid("operatorId"), open);
    }

    private ObjectNode capture(long sequence) {
        return SubmissionJson.object().put("id", UUID.randomUUID().toString()).put("originDeviceId", seed.get("deviceId"))
            .put("deviceCaptureSeq", Long.toString(sequence)).put("orderScanSeq","1").put("state", "COMMITTED");
    }

    private ObjectNode scan(ObjectNode capture, UUID bale, long sequence) {
        return SubmissionJson.object().put("captureId", capture.path("id").asText()).put("baleId", bale.toString())
            .put("expectedDispatchCycle", "1").put("orderScanSeq","1").put("localSeq", Long.toString(sequence)).put("status", "ACTIVE");
    }

    static E2aPageItem item(String kind, JsonNode record) {
        boolean scan = "SCAN".equals(kind), audit = "AUDIT".equals(kind);
        UUID id = SubmissionJson.uuid(record, scan ? "captureId" : "id");
        return new E2aPageItem(kind, id, audit ? "AUDIT_RECORDS" : "CAPTURE_RECORDS",
            UUID.nameUUIDFromBytes((kind + "/" + id).getBytes(StandardCharsets.UTF_8)), SubmissionJson.sha(record),
            bytes(record).length, SubmissionJson.number(record, scan || audit ? "localSeq" : "deviceCaptureSeq"),
            SubmissionJson.sha(record), audit ? "SAVED" : SubmissionJson.text(record, scan ? "status" : "state"),
            scan ? SubmissionJson.uuid(record, "baleId") : null, scan ? 1L : null, scan ? id : null);
    }

    UUID uuid(String key) { return UUID.fromString(seed.get(key)); }
    byte[] id(String key) { return Ids.bytes(uuid(key)); }
    static byte[] bytes(JsonNode value) { return SubmissionJson.canonical(value).getBytes(StandardCharsets.UTF_8); }
}
