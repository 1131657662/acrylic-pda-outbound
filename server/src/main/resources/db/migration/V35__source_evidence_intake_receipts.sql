SET SESSION lock_wait_timeout=10;
CREATE TABLE source_evidence_intake_receipt (
 id BINARY(16) PRIMARY KEY,
 receipt_version INT NOT NULL,
 principal_id BINARY(16) NOT NULL,
 route_scope VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 original_key BINARY(16) NOT NULL,
 current_session_id BINARY(16) NULL,
 warehouse_id BINARY(16) NOT NULL,
 scope_id BINARY(16) NOT NULL,
 submission_id BINARY(16) NOT NULL,
 work_round BIGINT NOT NULL,
 creator_device_id BINARY(16) NOT NULL,
 client_order_id BINARY(16) NOT NULL,
 temporary_authorization_id BINARY(16) NOT NULL,
 original_operator_id BINARY(16) NOT NULL,
 source_recovery_epoch BIGINT NOT NULL,
 operation_kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 receipt_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 canonical_request_bytes MEDIUMBLOB NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 original_response_bytes MEDIUMBLOB NOT NULL,
 original_response_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 signature_key_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 signed_receipt MEDIUMTEXT CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 object_id BINARY(16) NULL,
 evidence_set_id BINARY(16) NULL,
 evidence_verification_id BINARY(16) NULL,
 recorded_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_evidence_intake_original(principal_id,route_scope,original_key),
 KEY ix_evidence_intake_source(warehouse_id,scope_id,submission_id,work_round,recorded_at,id),
 FOREIGN KEY(principal_id) REFERENCES app_user(id),
 FOREIGN KEY(current_session_id) REFERENCES pda_session(id),
 FOREIGN KEY(warehouse_id,scope_id) REFERENCES submission_scope(warehouse_id,id),
 FOREIGN KEY(scope_id,creator_device_id,client_order_id) REFERENCES submission_scope(id,creator_device_id,client_order_id),
 FOREIGN KEY(scope_id,submission_id,work_round) REFERENCES submission(scope_id,id,work_round),
 FOREIGN KEY(submission_id,temporary_authorization_id,creator_device_id,original_operator_id,source_recovery_epoch)
  REFERENCES submission(id,temporary_authorization_id,freeze_device_id,operator_id,source_recovery_epoch),
 FOREIGN KEY(object_id,scope_id,submission_id,work_round)
  REFERENCES source_evidence_object(id,scope_id,submission_id,work_round),
 FOREIGN KEY(evidence_set_id,scope_id,submission_id,work_round)
  REFERENCES source_evidence_set(id,scope_id,submission_id,work_round),
 FOREIGN KEY(evidence_verification_id,evidence_set_id,scope_id)
  REFERENCES source_evidence_verification(id,set_id,scope_id),
 CHECK(receipt_version=1),
 CHECK(principal_id=original_operator_id),
 CHECK(work_round>0 AND source_recovery_epoch>=0),
 CHECK(operation_kind IN ('OPEN','PAGE','STOP','VERIFY')),
 CHECK(receipt_status IN ('RECEIVED','PARTIAL','CONFLICT','UNVERIFIABLE','VERIFIED_COMPLETE_AT_STOP')),
 CHECK(OCTET_LENGTH(canonical_request_bytes) BETWEEN 1 AND 262144),
 CHECK(OCTET_LENGTH(original_response_bytes) BETWEEN 1 AND 262144),
 CHECK(request_sha256 REGEXP '^[0-9a-f]{64}$'),
 CHECK(original_response_sha256 REGEXP '^[0-9a-f]{64}$'),
 CHECK((operation_kind='OPEN' AND object_id IS NULL AND evidence_set_id IS NULL AND evidence_verification_id IS NULL)
    OR operation_kind<>'OPEN'),
 CHECK(evidence_verification_id IS NULL OR evidence_set_id IS NOT NULL)
) ENGINE=InnoDB;
CREATE TRIGGER trg_evidence_intake_no_update BEFORE UPDATE ON source_evidence_intake_receipt
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='source evidence intake receipts are immutable';
CREATE TRIGGER trg_evidence_intake_no_delete BEFORE DELETE ON source_evidence_intake_receipt
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='source evidence intake receipts are immutable';
