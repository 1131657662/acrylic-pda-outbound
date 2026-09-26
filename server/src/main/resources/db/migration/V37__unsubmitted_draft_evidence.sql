SET SESSION lock_wait_timeout=10;
ALTER TABLE temporary_authorization ADD UNIQUE KEY uq_temp_draft_evidence_identity(id,warehouse_id,device_id,user_id,recovery_epoch);
CREATE TABLE local_draft_evidence (
 evidence_id BINARY(16) PRIMARY KEY,
 client_order_id BINARY(16) NOT NULL, warehouse_id BINARY(16) NOT NULL,
 creator_device_id BINARY(16) NOT NULL, original_operator_id BINARY(16) NOT NULL,
 authorization_id BINARY(16) NOT NULL, source_recovery_epoch BIGINT NOT NULL,
 open_key BINARY(16) NOT NULL, open_bytes MEDIUMBLOB NOT NULL,
 open_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 root_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 record_count INT NOT NULL, byte_count BIGINT NOT NULL, received_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_draft_evidence_source(creator_device_id,client_order_id),
 UNIQUE KEY uq_draft_evidence_identity(evidence_id,original_operator_id,creator_device_id,warehouse_id),
 UNIQUE KEY uq_draft_evidence_open(original_operator_id,open_key),
 FOREIGN KEY(authorization_id,warehouse_id,creator_device_id,original_operator_id,source_recovery_epoch)
  REFERENCES temporary_authorization(id,warehouse_id,device_id,user_id,recovery_epoch),
 CHECK(record_count BETWEEN 1 AND 250000), CHECK(byte_count BETWEEN 1 AND 134217728),
 CHECK(source_recovery_epoch BETWEEN 1 AND 9007199254740991),
 CHECK(OCTET_LENGTH(open_bytes) BETWEEN 1 AND 262144),
 CHECK(open_sha256 REGEXP '^[0-9a-f]{64}$'), CHECK(root_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB;
CREATE TABLE local_draft_evidence_receipt (
 principal_id BINARY(16) NOT NULL, original_key BINARY(16) NOT NULL,
 evidence_id BINARY(16) NOT NULL, creator_device_id BINARY(16) NOT NULL, warehouse_id BINARY(16) NOT NULL,
 operation_kind VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_bytes MEDIUMBLOB NOT NULL, request_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 response_bytes MEDIUMBLOB NOT NULL, response_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 recorded_at DATETIME(3) NOT NULL,
 PRIMARY KEY(principal_id,original_key),
 UNIQUE KEY uq_draft_receipt_completion(evidence_id,principal_id,original_key,operation_kind),
 FOREIGN KEY(evidence_id,principal_id,creator_device_id,warehouse_id)
  REFERENCES local_draft_evidence(evidence_id,original_operator_id,creator_device_id,warehouse_id),
 CHECK(operation_kind IN ('OPEN','ITEM','VERIFY')),
 CHECK(OCTET_LENGTH(request_bytes) BETWEEN 1 AND 262144), CHECK(OCTET_LENGTH(response_bytes) BETWEEN 1 AND 262144),
 CHECK(request_sha256 REGEXP '^[0-9a-f]{64}$'), CHECK(response_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB;
CREATE TABLE local_draft_evidence_item (
 evidence_id BINARY(16) NOT NULL, ordinal INT NOT NULL,
 item_kind VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, record_id BINARY(16) NOT NULL,
 device_seq BIGINT NOT NULL, record_status VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 byte_count INT NOT NULL, bytes_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 file_key VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 PRIMARY KEY(evidence_id,ordinal), UNIQUE KEY uq_draft_item_identity(evidence_id,item_kind,record_id),
 FOREIGN KEY(evidence_id) REFERENCES local_draft_evidence(evidence_id),
 CHECK(ordinal BETWEEN 1 AND 250000), CHECK(item_kind IN ('CAPTURE','SCAN','AUDIT','ARCHIVE')),
 CHECK(device_seq BETWEEN 1 AND 9007199254740991), CHECK(byte_count BETWEEN 1 AND 33554432),
 CHECK(bytes_sha256 REGEXP '^[0-9a-f]{64}$'), CHECK(CHAR_LENGTH(record_status) BETWEEN 1 AND 40)
) ENGINE=InnoDB;
CREATE TABLE local_draft_evidence_complete (
 evidence_id BINARY(16) PRIMARY KEY, principal_id BINARY(16) NOT NULL, original_key BINARY(16) NOT NULL,
 operation_kind VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'VERIFY',
 completed_at DATETIME(3) NOT NULL,
 FOREIGN KEY(evidence_id) REFERENCES local_draft_evidence(evidence_id),
 FOREIGN KEY(evidence_id,principal_id,original_key,operation_kind)
  REFERENCES local_draft_evidence_receipt(evidence_id,principal_id,original_key,operation_kind),
 CHECK(operation_kind='VERIFY')
) ENGINE=InnoDB;
CREATE TRIGGER trg_draft_evidence_no_update BEFORE UPDATE ON local_draft_evidence
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='original draft evidence is immutable';
CREATE TRIGGER trg_draft_evidence_no_delete BEFORE DELETE ON local_draft_evidence
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='original draft evidence is immutable';
CREATE TRIGGER trg_draft_receipt_no_update BEFORE UPDATE ON local_draft_evidence_receipt
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='original draft receipt is immutable';
CREATE TRIGGER trg_draft_receipt_no_delete BEFORE DELETE ON local_draft_evidence_receipt
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='original draft receipt is immutable';
CREATE TRIGGER trg_draft_item_no_update BEFORE UPDATE ON local_draft_evidence_item
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='original draft item is immutable';
CREATE TRIGGER trg_draft_item_no_delete BEFORE DELETE ON local_draft_evidence_item
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='original draft item is immutable';
CREATE TRIGGER trg_draft_complete_no_update BEFORE UPDATE ON local_draft_evidence_complete
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='original draft completion is immutable';
CREATE TRIGGER trg_draft_complete_no_delete BEFORE DELETE ON local_draft_evidence_complete
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='original draft completion is immutable';
