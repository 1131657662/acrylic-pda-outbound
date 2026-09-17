SET SESSION lock_wait_timeout=10;
CREATE TABLE submission_scope (
 id BINARY(16) PRIMARY KEY, warehouse_id BINARY(16) NOT NULL, source_order_id BINARY(16) NOT NULL UNIQUE,
 work_round BIGINT NOT NULL DEFAULT 1, current_submission_id BINARY(16) NULL,
 FOREIGN KEY(warehouse_id,source_order_id) REFERENCES outbound_order(warehouse_id,id), CHECK(work_round>0)
) ENGINE=InnoDB;
CREATE TABLE submission (
 id BINARY(16) PRIMARY KEY, scope_id BINARY(16) NOT NULL, assignment_id BINARY(16) NOT NULL,
 freeze_device_id BINARY(16) NOT NULL, operator_id BINARY(16) NOT NULL, work_round BIGINT NOT NULL,
 source_recovery_epoch BIGINT NOT NULL, status VARCHAR(30) NOT NULL,
 issues_json JSON NULL, receipt_text MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
 created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 FOREIGN KEY(scope_id) REFERENCES submission_scope(id), FOREIGN KEY(assignment_id) REFERENCES order_assignment(id),
 FOREIGN KEY(freeze_device_id) REFERENCES device(id), FOREIGN KEY(operator_id) REFERENCES app_user(id),
 KEY ix_submission_review(status,created_at,id), KEY ix_submission_scope(scope_id,id),
 CHECK(work_round>0), CHECK(source_recovery_epoch>0),
 CHECK(status IN ('REGISTERED','RECEIVING','REVIEW_REQUIRED','REJECTED','ABORTED','SUCCEEDED')),
 CHECK(status NOT IN ('SUCCEEDED','ABORTED') OR receipt_text IS NOT NULL)
) ENGINE=InnoDB;
ALTER TABLE submission_scope ADD FOREIGN KEY(current_submission_id) REFERENCES submission(id);
CREATE TABLE order_submission_slot (
 order_id BINARY(16) PRIMARY KEY, scope_id BINARY(16) NOT NULL UNIQUE, work_round BIGINT NOT NULL,
 current_submission_id BINARY(16) NOT NULL, phase VARCHAR(20) NOT NULL,
 FOREIGN KEY(order_id) REFERENCES outbound_order(id), FOREIGN KEY(scope_id) REFERENCES submission_scope(id),
 FOREIGN KEY(current_submission_id) REFERENCES submission(id), CHECK(work_round>0),
 CHECK(phase IN ('FROZEN','COMPLETED'))
) ENGINE=InnoDB;
CREATE TABLE submission_payload (
 submission_id BINARY(16) PRIMARY KEY, schema_version INT NOT NULL, payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 file_key VARCHAR(160) CHARACTER SET ascii NOT NULL, byte_count INT NOT NULL,
 expected_count BIGINT NOT NULL, expected_weight_g BIGINT NOT NULL, capture_high_watermark BIGINT NOT NULL,
 received_at DATETIME(3) NOT NULL,
 FOREIGN KEY(submission_id) REFERENCES submission(id), CHECK(schema_version=2), CHECK(byte_count>0),
 CHECK(expected_count>0), CHECK(expected_weight_g>0), CHECK(capture_high_watermark>=expected_count)
) ENGINE=InnoDB;
CREATE TABLE submission_upload (
 submission_id BINARY(16) PRIMARY KEY, payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 byte_count INT NOT NULL, chunk_count INT NOT NULL,
 FOREIGN KEY(submission_id) REFERENCES submission(id), CHECK(byte_count>0 AND byte_count<=33554432),
 CHECK(chunk_count>0 AND chunk_count<=128)
) ENGINE=InnoDB;
CREATE TABLE submission_chunk (
 submission_id BINARY(16) NOT NULL, chunk_index INT NOT NULL, byte_count INT NOT NULL,
 sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, file_key VARCHAR(160) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(submission_id,chunk_index), FOREIGN KEY(submission_id) REFERENCES submission_upload(submission_id),
 CHECK(chunk_index>=0 AND chunk_index<128), CHECK(byte_count>0 AND byte_count<=262144)
) ENGINE=InnoDB;
CREATE TABLE submitted_capture (
 id BINARY(16) PRIMARY KEY, submission_id BINARY(16) NOT NULL, order_id BINARY(16) NOT NULL,
 origin_device_id BINARY(16) NOT NULL, device_capture_seq BIGINT NOT NULL, order_scan_seq BIGINT NOT NULL,
 record_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 UNIQUE KEY uq_capture_device(origin_device_id,device_capture_seq), UNIQUE KEY uq_capture_order(order_id,order_scan_seq),
 FOREIGN KEY(submission_id) REFERENCES submission(id), FOREIGN KEY(order_id) REFERENCES outbound_order(id),
 FOREIGN KEY(origin_device_id) REFERENCES device(id), CHECK(device_capture_seq>0), CHECK(order_scan_seq>0)
) ENGINE=InnoDB;
CREATE TABLE submitted_event (
 id BINARY(16) PRIMARY KEY, submission_id BINARY(16) NOT NULL, origin_device_id BINARY(16) NOT NULL,
 local_seq BIGINT NOT NULL, record_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 UNIQUE KEY uq_event_device(origin_device_id,local_seq),
 FOREIGN KEY(submission_id) REFERENCES submission(id), FOREIGN KEY(origin_device_id) REFERENCES device(id), CHECK(local_seq>0)
) ENGINE=InnoDB;
CREATE TABLE bale_commitment (
 bale_id BINARY(16) PRIMARY KEY, order_id BINARY(16) NOT NULL, submission_id BINARY(16) NOT NULL,
 dispatch_cycle BIGINT NOT NULL, state VARCHAR(30) NOT NULL, created_at DATETIME(3) NOT NULL,
 KEY ix_commitment_order(order_id,bale_id), FOREIGN KEY(bale_id) REFERENCES bale(id),
 FOREIGN KEY(order_id) REFERENCES outbound_order(id), FOREIGN KEY(submission_id) REFERENCES submission(id),
 CHECK(dispatch_cycle>0), CHECK(state IN ('REVIEW_RESERVED','DISPATCHED'))
) ENGINE=InnoDB;
CREATE TABLE dispatch_record (
 id BINARY(16) PRIMARY KEY, submission_id BINARY(16) NOT NULL, order_id BINARY(16) NOT NULL,
 capture_id BINARY(16) NOT NULL UNIQUE, bale_id BINARY(16) NOT NULL, dispatch_cycle BIGINT NOT NULL,
 order_scan_seq BIGINT NOT NULL, delivery_code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 weight_g BIGINT NOT NULL, raw_weight VARCHAR(20) NOT NULL, model_snapshot VARCHAR(100) NOT NULL, name_snapshot VARCHAR(100) NOT NULL,
 confirmed_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_dispatch_cycle(bale_id,dispatch_cycle), UNIQUE KEY uq_dispatch_order_seq(order_id,order_scan_seq),
 FOREIGN KEY(submission_id) REFERENCES submission(id), FOREIGN KEY(order_id) REFERENCES outbound_order(id),
 FOREIGN KEY(capture_id) REFERENCES submitted_capture(id), FOREIGN KEY(bale_id) REFERENCES bale(id),
 CHECK(dispatch_cycle>0), CHECK(weight_g>0), CHECK(order_scan_seq>0)
) ENGINE=InnoDB;
CREATE TABLE inventory_ledger (
 id BINARY(16) PRIMARY KEY, dispatch_id BINARY(16) NOT NULL UNIQUE, bale_id BINARY(16) NOT NULL,
 quantity_delta INT NOT NULL, weight_delta_g BIGINT NOT NULL, created_at DATETIME(3) NOT NULL,
 FOREIGN KEY(dispatch_id) REFERENCES dispatch_record(id), FOREIGN KEY(bale_id) REFERENCES bale(id),
 KEY ix_ledger_bale(bale_id,id), CHECK(quantity_delta=-1), CHECK(weight_delta_g<0)
) ENGINE=InnoDB;
CREATE TABLE order_result_version (
 order_id BINARY(16) NOT NULL, result_version BIGINT NOT NULL, submission_id BINARY(16) NOT NULL UNIQUE,
 actual_count BIGINT NOT NULL, weight_g BIGINT NOT NULL, inventory_deducted BOOLEAN NOT NULL,
 header_snapshot JSON NOT NULL, created_at DATETIME(3) NOT NULL,
 PRIMARY KEY(order_id,result_version), FOREIGN KEY(order_id) REFERENCES outbound_order(id),
 FOREIGN KEY(submission_id) REFERENCES submission(id), CHECK(result_version>0), CHECK(actual_count>0), CHECK(weight_g>0)
) ENGINE=InnoDB;
CREATE TABLE order_effective_line (
 order_id BINARY(16) NOT NULL, result_version BIGINT NOT NULL, delivery_code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actual_count BIGINT NOT NULL, weight_g BIGINT NOT NULL,
 PRIMARY KEY(order_id,result_version,delivery_code), FOREIGN KEY(order_id,result_version) REFERENCES order_result_version(order_id,result_version),
 CHECK(actual_count>=0), CHECK(weight_g>=0)
) ENGINE=InnoDB;
ALTER TABLE outbound_order ADD current_result_version BIGINT NULL,
 ADD FOREIGN KEY(id,current_result_version) REFERENCES order_result_version(order_id,result_version);
CREATE TABLE submission_review (
 submission_id BINARY(16) PRIMARY KEY, payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 decision VARCHAR(20) NOT NULL, reason VARCHAR(500) NOT NULL, actor_id BINARY(16) NOT NULL,
 permission_version BIGINT NOT NULL, created_at DATETIME(3) NOT NULL,
 FOREIGN KEY(submission_id) REFERENCES submission(id), FOREIGN KEY(actor_id) REFERENCES app_user(id),
 CHECK(decision IN ('APPROVE','REJECT'))
) ENGINE=InnoDB;
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000035'),'pda:submit','提交及查询本设备冻结出库记录');
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000036'),'submission:review','审核短装出库及退回待处理');
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p
 WHERE r.code IN ('WAREHOUSE_ADMIN','PDA_OPERATOR') AND p.code='pda:submit';
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p
 WHERE r.code='WAREHOUSE_ADMIN' AND p.code='submission:review';
