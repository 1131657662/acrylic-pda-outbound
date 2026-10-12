-- A FORMAL origin retains its own approved/applied evidence and a fixed proof digest.
SET SESSION lock_wait_timeout=10;
CREATE TABLE formal_disposition_receipt (
    approval_batch_id BINARY(16) PRIMARY KEY,
    recovery_package_id BINARY(16) NOT NULL,
    principal_id BINARY(16) NOT NULL,
    request_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    command_bytes LONGBLOB NOT NULL,
    command_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    response_bytes LONGBLOB NOT NULL,
    response_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    FOREIGN KEY(recovery_package_id) REFERENCES recovery_package(id),
    FOREIGN KEY(principal_id) REFERENCES app_user(id),
    CHECK(request_sha256 REGEXP '^[a-f0-9]{64}$' AND command_sha256 REGEXP '^[a-f0-9]{64}$' AND response_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB;
DELIMITER $$
CREATE TRIGGER formal_disposition_receipt_no_update BEFORE UPDATE ON formal_disposition_receipt
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Original FORMAL disposition receipt is immutable'; END$$
CREATE TRIGGER formal_disposition_receipt_no_delete BEFORE DELETE ON formal_disposition_receipt
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Original FORMAL disposition receipt must be retained'; END$$
DELIMITER ;
ALTER TABLE quarantine_origin
    DROP CHECK ck_quarantine_origin_type,
    ADD formal_disposition_id BINARY(16) NULL,
    ADD formal_evidence_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD UNIQUE KEY uq_quarantine_formal_origin(formal_disposition_id,bale_id),
    ADD CONSTRAINT fk_quarantine_formal_origin FOREIGN KEY(formal_disposition_id) REFERENCES physical_disposition_evidence(id),
    ADD CONSTRAINT ck_quarantine_origin_type CHECK (
      (disposition_id IS NOT NULL AND original_receipt_id IS NOT NULL AND pc_disposition_id IS NULL AND pc_evidence_sha256 IS NULL
       AND formal_disposition_id IS NULL AND formal_evidence_sha256 IS NULL) OR
      (disposition_id IS NULL AND original_receipt_id IS NULL AND pc_disposition_id IS NOT NULL
       AND pc_evidence_sha256 IS NOT NULL AND pc_evidence_sha256 REGEXP '^[a-f0-9]{64}$'
       AND formal_disposition_id IS NULL AND formal_evidence_sha256 IS NULL) OR
      (disposition_id IS NULL AND original_receipt_id IS NULL AND pc_disposition_id IS NULL AND pc_evidence_sha256 IS NULL
       AND formal_disposition_id IS NOT NULL AND formal_evidence_sha256 IS NOT NULL AND formal_evidence_sha256 REGEXP '^[a-f0-9]{64}$'));
