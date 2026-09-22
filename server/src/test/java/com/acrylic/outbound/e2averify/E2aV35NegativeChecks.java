package com.acrylic.outbound.e2averify;

import org.springframework.jdbc.core.JdbcTemplate;

/** W2-owned negative-check entry point; each check must assert rollback independently. */
public final class E2aV35NegativeChecks {
    private E2aV35NegativeChecks() { }
    public static void verify(JdbcTemplate jdbc) {
        requireTable(jdbc, "source_evidence_intake_receipt");
        requireTable(jdbc, "source_evidence_object");
        requireTable(jdbc, "source_evidence_set");
        requireTable(jdbc, "source_evidence_verification");
        expectFailure(jdbc, "UPDATE source_evidence_intake_receipt SET receipt_version=2", "immutable update");
        expectFailure(jdbc, "DELETE FROM source_evidence_intake_receipt", "immutable delete");
        expectFailure(jdbc, "INSERT INTO source_evidence_intake_receipt (id,receipt_version,principal_id,route_scope,original_key,warehouse_id,scope_id,submission_id,work_round,creator_device_id,client_order_id,temporary_authorization_id,original_operator_id,source_recovery_epoch,operation_kind,receipt_status,canonical_request_bytes,request_sha256,original_response_bytes,original_response_sha256,signature_key_id,signed_receipt,recorded_at) VALUES (UUID_TO_BIN(UUID()),1,UUID_TO_BIN(UUID()),'bad',UUID_TO_BIN(UUID()),UUID_TO_BIN(UUID()),UUID_TO_BIN(UUID()),UUID_TO_BIN(UUID()),1,UUID_TO_BIN(UUID()),UUID_TO_BIN(UUID()),UUID_TO_BIN(UUID()),UUID_TO_BIN(UUID()),1,'OPEN','RECEIVED','{}',REPEAT('0',64),'{}',REPEAT('0',64),'k','jwt',UTC_TIMESTAMP(3))", "invalid FK/domain");
    }

    private static void expectFailure(JdbcTemplate jdbc, String sql, String label) {
        try {
            jdbc.update(sql);
            throw new IllegalStateException("Negative case unexpectedly succeeded: " + label);
        } catch (org.springframework.dao.DataAccessException expected) {
            // Expected: caller records this SQL failure in the dynamic evidence log.
        }
    }

    private static void requireTable(JdbcTemplate jdbc, String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?", Integer.class, table);
        if (!Integer.valueOf(1).equals(count)) throw new IllegalStateException("Missing V35 table: " + table);
    }
}
