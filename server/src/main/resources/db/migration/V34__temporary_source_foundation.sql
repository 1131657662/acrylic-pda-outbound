SET SESSION lock_wait_timeout=10;
-- All preflight checks precede permanent DDL. Failure keeps V33 facts unchanged.
CREATE TEMPORARY TABLE r06_v34_guard (valid INT NOT NULL, CONSTRAINT ck_v34_source_preflight CHECK(valid=1));
INSERT INTO r06_v34_guard SELECT IF(COUNT(*)=0,1,0) FROM submission_scope c
 LEFT JOIN temporary_work_origin t ON t.creator_device_id=c.creator_device_id AND t.client_order_id=c.client_order_id
 LEFT JOIN temporary_authorization a ON a.id=t.authorization_id
 WHERE c.creator_device_id IS NOT NULL AND (a.id IS NULL OR a.warehouse_id<>c.warehouse_id);
INSERT INTO r06_v34_guard SELECT IF(COUNT(*)=0,1,0) FROM submission s JOIN submission_scope c ON c.id=s.scope_id
 LEFT JOIN temporary_work_origin t ON t.creator_device_id=c.creator_device_id AND t.client_order_id=c.client_order_id
 LEFT JOIN temporary_authorization a ON a.id=t.authorization_id
 WHERE c.creator_device_id IS NOT NULL AND (s.temporary_authorization_id IS NULL OR a.id IS NULL OR s.temporary_authorization_id<>a.id
 OR s.freeze_device_id<>c.creator_device_id OR s.operator_id<>a.user_id OR s.source_recovery_epoch<>a.recovery_epoch OR s.work_round>c.work_round);
INSERT INTO r06_v34_guard SELECT IF(COUNT(*)=0,1,0) FROM submission_scope c LEFT JOIN submission s ON s.id=c.current_submission_id
 WHERE c.creator_device_id IS NOT NULL AND c.current_submission_id IS NOT NULL AND (s.id IS NULL OR s.scope_id<>c.id OR s.work_round<>c.work_round);
INSERT INTO r06_v34_guard SELECT IF(COUNT(*)=0,1,0) FROM bale_commitment k JOIN submission s ON s.id=k.submission_id
 JOIN submission_scope c ON c.id=s.scope_id JOIN bale b ON b.id=k.bale_id
 WHERE c.creator_device_id IS NOT NULL AND (c.canonical_order_id IS NULL OR k.order_id<>c.canonical_order_id OR b.warehouse_id<>c.warehouse_id);
DROP TEMPORARY TABLE r06_v34_guard;
ALTER TABLE submission_scope ADD mapping_version BIGINT NOT NULL DEFAULT 0,
 ADD temporary_scope_id BINARY(16) GENERATED ALWAYS AS (IF(creator_device_id IS NOT NULL,id,NULL)) STORED,
 ADD UNIQUE KEY uq_scope_temporary(temporary_scope_id),
 ADD UNIQUE KEY uq_scope_warehouse(warehouse_id,id), ADD UNIQUE KEY uq_scope_source(id,creator_device_id,client_order_id), ADD CHECK(mapping_version>=0);
ALTER TABLE submission ADD UNIQUE KEY uq_submission_scope_id(scope_id,id), ADD UNIQUE KEY uq_submission_context_round(scope_id,id,work_round),
 ADD UNIQUE KEY uq_submission_device(scope_id,id,work_round,freeze_device_id),
 ADD UNIQUE KEY uq_submission_origin(id,temporary_authorization_id,freeze_device_id,operator_id,source_recovery_epoch);
ALTER TABLE order_submission_slot ADD slot_version BIGINT NOT NULL DEFAULT 1, ADD CHECK(slot_version>0);
CREATE TABLE temporary_source_mapping (
 id BINARY(16) PRIMARY KEY, warehouse_id BINARY(16) NOT NULL, scope_id BINARY(16) NOT NULL, mapping_version BIGINT NOT NULL,
 canonical_order_id BINARY(16) NULL, origin VARCHAR(24) NOT NULL, observed_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_source_mapping(scope_id,mapping_version), UNIQUE KEY uq_source_mapping_id(id,scope_id),
 FOREIGN KEY(warehouse_id,scope_id) REFERENCES submission_scope(warehouse_id,id),
 FOREIGN KEY(warehouse_id,canonical_order_id) REFERENCES outbound_order(warehouse_id,id),
 CHECK((mapping_version=0 AND canonical_order_id IS NULL) OR (mapping_version>0 AND canonical_order_id IS NOT NULL)),
 CHECK(origin IN ('MIGRATED_BASELINE','INITIAL_ACCEPTANCE','SOURCE_REGISTERED'))
) ENGINE=InnoDB;
CREATE TABLE temporary_decision_state (
 scope_id BINARY(16) PRIMARY KEY, decision_version BIGINT NOT NULL, origin VARCHAR(24) NOT NULL, initialized_at DATETIME(3) NOT NULL,
 FOREIGN KEY(scope_id) REFERENCES submission_scope(id), CHECK(decision_version>=0), CHECK(origin IN ('MIGRATED_BASELINE','SOURCE_REGISTERED'))
) ENGINE=InnoDB;
UPDATE submission_scope SET mapping_version=IF(canonical_order_id IS NULL,0,1) WHERE creator_device_id IS NOT NULL;
INSERT INTO temporary_source_mapping SELECT UUID_TO_BIN(UUID()),warehouse_id,id,mapping_version,canonical_order_id,'MIGRATED_BASELINE',UTC_TIMESTAMP(3)
 FROM submission_scope WHERE creator_device_id IS NOT NULL;
INSERT INTO temporary_decision_state SELECT id,0,'MIGRATED_BASELINE',UTC_TIMESTAMP(3) FROM submission_scope WHERE creator_device_id IS NOT NULL;
CREATE TABLE source_evidence_object (
 id BINARY(16) PRIMARY KEY, warehouse_id BINARY(16) NOT NULL, scope_id BINARY(16) NOT NULL, submission_id BINARY(16) NOT NULL, work_round BIGINT NOT NULL,
 kind VARCHAR(32) NOT NULL, original_object_id BINARY(16) NOT NULL, bytes_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 byte_count BIGINT NOT NULL, file_key VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 origin_device_id BINARY(16) NOT NULL, uploaded_by BINARY(16) NOT NULL, received_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_evidence_object(scope_id,submission_id,work_round,kind,original_object_id), UNIQUE KEY uq_evidence_object_parent(id,scope_id,submission_id,work_round),
 FOREIGN KEY(warehouse_id,scope_id) REFERENCES submission_scope(warehouse_id,id), FOREIGN KEY(scope_id,submission_id,work_round) REFERENCES submission(scope_id,id,work_round),
 FOREIGN KEY(warehouse_id,origin_device_id) REFERENCES device(warehouse_id,id), FOREIGN KEY(uploaded_by) REFERENCES app_user(id),
 CHECK(kind IN ('LOCAL_FROZEN_PAYLOAD','STOP_PROOF','CAPTURE_RECORDS','AUDIT_RECORDS','AUDIT_ARCHIVE','MANIFEST_PAGE')),
 CHECK(work_round>0), CHECK(byte_count>0 AND byte_count<=33554432)
) ENGINE=InnoDB;
CREATE TABLE source_evidence_set (
 id BINARY(16) PRIMARY KEY, warehouse_id BINARY(16) NOT NULL, scope_id BINARY(16) NOT NULL, submission_id BINARY(16) NOT NULL, work_round BIGINT NOT NULL,
 stop_object_id BINARY(16) NOT NULL, manifest_bytes MEDIUMBLOB NOT NULL, set_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 capture_high_watermark BIGINT NOT NULL, audit_through_seq BIGINT NOT NULL, item_count BIGINT NOT NULL, received_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_evidence_set_parent(id,scope_id,submission_id,work_round), UNIQUE KEY uq_evidence_set_scope(id,scope_id),
 FOREIGN KEY(warehouse_id,scope_id) REFERENCES submission_scope(warehouse_id,id), FOREIGN KEY(scope_id,submission_id,work_round) REFERENCES submission(scope_id,id,work_round),
 FOREIGN KEY(stop_object_id,scope_id,submission_id,work_round) REFERENCES source_evidence_object(id,scope_id,submission_id,work_round),
 CHECK(work_round>0), CHECK(capture_high_watermark>=0), CHECK(audit_through_seq>0), CHECK(item_count>=0), CHECK(OCTET_LENGTH(manifest_bytes) BETWEEN 1 AND 262144)
) ENGINE=InnoDB;
CREATE TABLE source_evidence_item (
 set_id BINARY(16) NOT NULL, warehouse_id BINARY(16) NOT NULL, scope_id BINARY(16) NOT NULL, submission_id BINARY(16) NOT NULL, work_round BIGINT NOT NULL,
 item_kind VARCHAR(24) NOT NULL, original_record_id BINARY(16) NOT NULL, object_id BINARY(16) NOT NULL, original_device_seq BIGINT NOT NULL,
 record_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, bale_id BINARY(16) NULL, original_cycle BIGINT NULL, capture_id BINARY(16) NULL, record_status VARCHAR(40) NOT NULL,
 PRIMARY KEY(set_id,item_kind,original_record_id), FOREIGN KEY(set_id,scope_id,submission_id,work_round) REFERENCES source_evidence_set(id,scope_id,submission_id,work_round),
 FOREIGN KEY(object_id,scope_id,submission_id,work_round) REFERENCES source_evidence_object(id,scope_id,submission_id,work_round), FOREIGN KEY(bale_id) REFERENCES bale(id),
 CHECK(item_kind IN ('CAPTURE','SCAN','AUDIT','STOP','ARCHIVE')), CHECK(original_device_seq>0),
 CHECK((item_kind='SCAN' AND bale_id IS NOT NULL AND original_cycle IS NOT NULL AND original_cycle>0 AND capture_id IS NOT NULL) OR
 (item_kind<>'SCAN' AND bale_id IS NULL AND original_cycle IS NULL AND capture_id IS NULL))
) ENGINE=InnoDB;
CREATE TABLE source_evidence_verification (
 id BINARY(16) PRIMARY KEY, set_id BINARY(16) NOT NULL, scope_id BINARY(16) NOT NULL, submission_id BINARY(16) NOT NULL, work_round BIGINT NOT NULL,
 verification_version BIGINT NOT NULL, algorithm_version BIGINT NOT NULL, set_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 result VARCHAR(32) NOT NULL, known_facts_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, findings_bytes MEDIUMBLOB NOT NULL, verified_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_evidence_verification(set_id,verification_version), UNIQUE KEY uq_evidence_verification_parent(id,set_id,scope_id),
 FOREIGN KEY(set_id,scope_id,submission_id,work_round) REFERENCES source_evidence_set(id,scope_id,submission_id,work_round),
 CHECK(verification_version>0), CHECK(algorithm_version>0), CHECK(result IN ('PARTIAL','VERIFIED_COMPLETE_AT_STOP','CONFLICT','UNVERIFIABLE')),
 CHECK(OCTET_LENGTH(findings_bytes) BETWEEN 1 AND 262144)
) ENGINE=InnoDB;
CREATE TABLE temporary_command_receipt (
 id BINARY(16) PRIMARY KEY, api_request_id BINARY(16) NOT NULL UNIQUE, warehouse_id BINARY(16) NOT NULL, scope_id BINARY(16) NOT NULL,
 submission_id BINARY(16) NOT NULL, work_round BIGINT NOT NULL, creator_device_id BINARY(16) NOT NULL, client_order_id BINARY(16) NOT NULL,
 temporary_authorization_id BINARY(16) NOT NULL, original_operator_id BINARY(16) NOT NULL, source_recovery_epoch BIGINT NOT NULL,
 original_canonical_order_id BINARY(16) NULL, original_mapping_id BINARY(16) NOT NULL, evidence_set_id BINARY(16) NULL, evidence_verification_id BINARY(16) NULL,
 command_version INT NOT NULL, command_type VARCHAR(24) NOT NULL, canonical_command_bytes MEDIUMBLOB NOT NULL, command_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 receipt_version INT NOT NULL, original_response_bytes MEDIUMBLOB NOT NULL, original_response_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 unsigned_receipt_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, signature_key_id VARCHAR(80) CHARACTER SET ascii NOT NULL,
 signed_receipt MEDIUMTEXT CHARACTER SET ascii COLLATE ascii_bin NOT NULL, recorded_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_command_receipt_scope(id,scope_id), KEY ix_command_history(warehouse_id,scope_id,recorded_at,id),
 FOREIGN KEY(api_request_id) REFERENCES api_idempotency_request(id), FOREIGN KEY(warehouse_id,scope_id) REFERENCES submission_scope(warehouse_id,id),
 FOREIGN KEY(scope_id,creator_device_id,client_order_id) REFERENCES submission_scope(id,creator_device_id,client_order_id),
 FOREIGN KEY(scope_id,submission_id,work_round) REFERENCES submission(scope_id,id,work_round),
 FOREIGN KEY(submission_id,temporary_authorization_id,creator_device_id,original_operator_id,source_recovery_epoch)
 REFERENCES submission(id,temporary_authorization_id,freeze_device_id,operator_id,source_recovery_epoch),
 FOREIGN KEY(warehouse_id,original_canonical_order_id) REFERENCES outbound_order(warehouse_id,id), FOREIGN KEY(original_mapping_id,scope_id) REFERENCES temporary_source_mapping(id,scope_id),
 FOREIGN KEY(evidence_set_id,scope_id,submission_id,work_round) REFERENCES source_evidence_set(id,scope_id,submission_id,work_round),
 FOREIGN KEY(evidence_verification_id,evidence_set_id,scope_id) REFERENCES source_evidence_verification(id,set_id,scope_id),
 CHECK(command_version=1), CHECK(receipt_version=1), CHECK(command_type IN ('DUPLICATE_EXCLUDE','LINK','REJECT','SHORT_APPROVE','NORMAL_CONFIRM','PHYSICAL_DISPOSITION','REOPEN_WORK')),
 CHECK((evidence_set_id IS NULL AND evidence_verification_id IS NULL) OR (evidence_set_id IS NOT NULL AND evidence_verification_id IS NOT NULL)),
 CHECK(OCTET_LENGTH(canonical_command_bytes) BETWEEN 1 AND 262144), CHECK(OCTET_LENGTH(original_response_bytes) BETWEEN 1 AND 262144)
) ENGINE=InnoDB;
CREATE TABLE temporary_decision_event (
 id BINARY(16) PRIMARY KEY, scope_id BINARY(16) NOT NULL, resulting_decision_version BIGINT NOT NULL, receipt_id BINARY(16) NOT NULL UNIQUE,
 decision_kind VARCHAR(24) NOT NULL, context_bytes MEDIUMBLOB NOT NULL, recorded_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_decision_event(scope_id,resulting_decision_version), FOREIGN KEY(scope_id) REFERENCES temporary_decision_state(scope_id),
 FOREIGN KEY(receipt_id,scope_id) REFERENCES temporary_command_receipt(id,scope_id), CHECK(resulting_decision_version>0),
 CHECK(OCTET_LENGTH(context_bytes) BETWEEN 1 AND 262144)
) ENGINE=InnoDB;
ALTER TABLE bale ADD UNIQUE KEY uq_bale_warehouse(warehouse_id,id);
ALTER TABLE temporary_source_mapping ADD FOREIGN KEY(scope_id) REFERENCES submission_scope(temporary_scope_id);
ALTER TABLE temporary_decision_state ADD FOREIGN KEY(scope_id) REFERENCES submission_scope(temporary_scope_id);
ALTER TABLE source_evidence_object ADD FOREIGN KEY(scope_id) REFERENCES submission_scope(temporary_scope_id),
 ADD FOREIGN KEY(scope_id,submission_id,work_round,origin_device_id) REFERENCES submission(scope_id,id,work_round,freeze_device_id);
ALTER TABLE source_evidence_set ADD FOREIGN KEY(scope_id) REFERENCES submission_scope(temporary_scope_id);
ALTER TABLE source_evidence_item ADD FOREIGN KEY(warehouse_id,scope_id) REFERENCES submission_scope(warehouse_id,id),
 ADD FOREIGN KEY(warehouse_id,bale_id) REFERENCES bale(warehouse_id,id);
ALTER TABLE bale_commitment ADD source_scope_id BINARY(16) NULL, ADD source_evidence_set_id BINARY(16) NULL,
 ADD source_warehouse_id BINARY(16) NULL,
 ADD FOREIGN KEY(source_scope_id) REFERENCES submission_scope(temporary_scope_id),
 ADD FOREIGN KEY(source_warehouse_id,source_scope_id) REFERENCES submission_scope(warehouse_id,id),
 ADD FOREIGN KEY(source_warehouse_id,order_id) REFERENCES outbound_order(warehouse_id,id),
 ADD FOREIGN KEY(source_warehouse_id,bale_id) REFERENCES bale(warehouse_id,id),
 ADD FOREIGN KEY(source_scope_id,submission_id) REFERENCES submission(scope_id,id),
 ADD FOREIGN KEY(source_evidence_set_id,source_scope_id) REFERENCES source_evidence_set(id,scope_id),
 ADD CONSTRAINT ck_commitment_source_warehouse CHECK((source_scope_id IS NULL AND source_warehouse_id IS NULL) OR (source_scope_id IS NOT NULL AND source_warehouse_id IS NOT NULL));
UPDATE bale_commitment k JOIN submission s ON s.id=k.submission_id JOIN submission_scope c ON c.id=s.scope_id
 SET k.source_scope_id=c.id,k.source_warehouse_id=c.warehouse_id WHERE c.creator_device_id IS NOT NULL;
ALTER TABLE bale_commitment MODIFY order_id BINARY(16) NULL, DROP CHECK ck_commitment_owner,
 ADD CONSTRAINT ck_commitment_owner CHECK (
 (source_evidence_set_id IS NULL AND order_id IS NOT NULL AND (
 (state IN ('REVIEW_RESERVED','DISPATCHED') AND submission_id IS NOT NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL) OR
 (state='RECOVERY_RESERVED' AND submission_id IS NULL AND recovery_package_id IS NOT NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL) OR
 (state='CONTINUATION_RESERVED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NOT NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL) OR
 (state='QUARANTINED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NOT NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL) OR
 (state IN ('PC_RESERVED','DISPATCHED') AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NOT NULL AND pc_disposition_id IS NULL) OR
 (state='QUARANTINED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NOT NULL))) OR
 (state='SOURCE_RESERVED' AND source_scope_id IS NOT NULL AND source_evidence_set_id IS NOT NULL AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL));
