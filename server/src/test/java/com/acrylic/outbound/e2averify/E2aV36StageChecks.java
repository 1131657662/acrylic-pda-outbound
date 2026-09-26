package com.acrylic.outbound.e2averify;

import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

final class E2aV36StageChecks {
    private static final String STAGE = "source_evidence_page_item_stage";
    private static final String INSERT = "INSERT INTO " + STAGE + " "
        + "(warehouse_id,scope_id,submission_id,work_round,principal_id,route_scope,open_original_key,"
        + "page_object_id,page_no,item_ordinal,item_kind,original_record_id,original_object_id,raw_object_id,byte_count) "
        + "VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,UUID_TO_BIN(?),"
        + "'r06.e2a.evidence.v1',UUID_TO_BIN(?),UUID_TO_BIN(?),1,?, 'SCAN',"
        + "UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1)";

    private E2aV36StageChecks() { }

    static void verify(JdbcTemplate jdbc, Map<String, String> seed) {
        String warehouse = seed.get("warehouseId");
        String scope = seed.get("mappedScopeId");
        String submission = seed.get("mappedSubmissionId");
        String operator = seed.get("operatorId");
        String open = UUID.randomUUID().toString();
        String page = object(jdbc, seed, "MANIFEST_PAGE", UUID.randomUUID().toString());
        String raw = object(jdbc, seed, "CAPTURE_RECORDS", UUID.randomUUID().toString());
        receipt(jdbc, seed, open);
        String nonOpen = UUID.randomUUID().toString();
        pageReceipt(jdbc, seed, nonOpen, page);
        int initial = count(jdbc);
        String record = UUID.randomUUID().toString();
        String original = UUID.randomUUID().toString();
        insert(jdbc, warehouse, scope, submission, operator, open, page, 0, record, original, raw);
        require(count(jdbc) == initial + 1, "Stage INSERT failed");
        reject(() -> insert(jdbc, warehouse, scope, submission, operator, open, page, 1, record, UUID.randomUUID().toString(), raw));
        reject(() -> insert(jdbc, warehouse, scope, submission, operator, open, page, 1, UUID.randomUUID().toString(), original, raw));
        reject(() -> insert(jdbc, warehouse, scope, submission, operator, UUID.randomUUID().toString(), page, 1,
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), raw));
        reject(() -> insert(jdbc, warehouse, scope, submission, operator, nonOpen, page, 1,
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), raw));
        reject(() -> insert(jdbc, warehouse, scope, submission, operator, open, page, 1,
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), seed.get("abortObjectId")));
        reject(() -> jdbc.update("UPDATE " + STAGE + " SET byte_count=2 WHERE page_object_id=UUID_TO_BIN(?)", page));
        reject(() -> jdbc.update("DELETE FROM " + STAGE + " WHERE page_object_id=UUID_TO_BIN(?)", page));
        require(count(jdbc) == initial + 1, "Stage mutated on rejected operation");
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.execute(status -> {
            insert(jdbc, warehouse, scope, submission, operator, open, page, 1,
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), raw);
            status.setRollbackOnly();
            return null;
        });
        require(count(jdbc) == initial + 1, "Rolled-back stage row persisted");
        require(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() "
            + "AND EVENT_OBJECT_TABLE=?", Integer.class, STAGE) == 2, "Stage immutability triggers missing");
    }

    private static String object(JdbcTemplate jdbc, Map<String, String> seed, String kind, String original) {
        String id = UUID.randomUUID().toString();
        int rows = jdbc.update("INSERT INTO source_evidence_object "
            + "(id,warehouse_id,scope_id,submission_id,work_round,kind,original_object_id,bytes_sha256,byte_count,"
            + "file_key,origin_device_id,uploaded_by,received_at) "
            + "VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,?,UUID_TO_BIN(?),REPEAT('a',64),1,?,"
            + "UUID_TO_BIN(?),UUID_TO_BIN(?),UTC_TIMESTAMP(3))",
            id, seed.get("warehouseId"), seed.get("mappedScopeId"), seed.get("mappedSubmissionId"), kind,
            original, "v36-" + id, seed.get("deviceId"), seed.get("operatorId"));
        require(rows == 1, "Object INSERT failed");
        return id;
    }

    static void receipt(JdbcTemplate jdbc, Map<String, String> seed, String open) {
        int rows = jdbc.update("INSERT INTO source_evidence_intake_receipt "
            + "(id,receipt_version,principal_id,route_scope,original_key,warehouse_id,scope_id,submission_id,work_round,"
            + "creator_device_id,client_order_id,temporary_authorization_id,original_operator_id,source_recovery_epoch,"
            + "operation_kind,receipt_status,canonical_request_bytes,request_sha256,original_response_bytes,"
            + "original_response_sha256,signature_key_id,signed_receipt,recorded_at) "
            + "SELECT UUID_TO_BIN(?),1,s.operator_id,'r06.e2a.evidence.v1',UUID_TO_BIN(?),c.warehouse_id,c.id,s.id,"
            + "s.work_round,c.creator_device_id,c.client_order_id,s.temporary_authorization_id,s.operator_id,"
            + "s.source_recovery_epoch,'OPEN','RECEIVED',CAST('{}' AS BINARY),REPEAT('a',64),CAST('{}' AS BINARY),"
            + "REPEAT('b',64),'v36-test','test-only',UTC_TIMESTAMP(3) FROM submission s "
            + "JOIN submission_scope c ON c.id=s.scope_id WHERE s.id=UUID_TO_BIN(?)", UUID.randomUUID().toString(),
            open, seed.get("mappedSubmissionId"));
        require(rows == 1, "OPEN INSERT failed");
    }

    private static void pageReceipt(JdbcTemplate jdbc, Map<String, String> seed, String key, String page) {
        int rows = jdbc.update("INSERT INTO source_evidence_intake_receipt "
            + "(id,receipt_version,principal_id,route_scope,original_key,warehouse_id,scope_id,submission_id,work_round,"
            + "creator_device_id,client_order_id,temporary_authorization_id,original_operator_id,source_recovery_epoch,"
            + "operation_kind,receipt_status,canonical_request_bytes,request_sha256,original_response_bytes,"
            + "original_response_sha256,signature_key_id,signed_receipt,object_id,recorded_at) "
            + "SELECT UUID_TO_BIN(?),1,s.operator_id,'r06.e2a.evidence.v1',UUID_TO_BIN(?),c.warehouse_id,c.id,s.id,"
            + "s.work_round,c.creator_device_id,c.client_order_id,s.temporary_authorization_id,s.operator_id,"
            + "s.source_recovery_epoch,'PAGE','PARTIAL',CAST('{}' AS BINARY),REPEAT('a',64),CAST('{}' AS BINARY),"
            + "REPEAT('b',64),'v36-test','test-only',UUID_TO_BIN(?),UTC_TIMESTAMP(3) FROM submission s "
            + "JOIN submission_scope c ON c.id=s.scope_id WHERE s.id=UUID_TO_BIN(?)", UUID.randomUUID().toString(),
            key, page, seed.get("mappedSubmissionId"));
        require(rows == 1, "PAGE receipt INSERT failed");
    }

    private static void insert(JdbcTemplate jdbc, String warehouse, String scope, String submission, String operator,
        String open, String page, int ordinal, String record, String original, String raw) {
        require(jdbc.update(INSERT, warehouse, scope, submission, operator, open, page, ordinal, record, original, raw)
            == 1, "Stage INSERT count");
    }

    private static int count(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + STAGE, Integer.class);
    }

    private static void reject(Runnable operation) {
        try {
            operation.run();
        } catch (DataAccessException expected) {
            return;
        }
        throw new AssertionError("Invalid stage mutation was accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
