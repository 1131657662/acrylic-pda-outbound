SET SESSION lock_wait_timeout=10;
CREATE TABLE order_change_request (
 id BINARY(16) PRIMARY KEY, warehouse_id BINARY(16) NOT NULL, order_id BINARY(16) NOT NULL,
 assignment_id BINARY(16) NOT NULL UNIQUE, revision INT NOT NULL, recovery_epoch BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL, reason VARCHAR(500) NOT NULL, requested_by BINARY(16) NOT NULL,
 stop_receipt MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
 resolution_receipt MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
 resolved_by BINARY(16) NULL, physical_evidence VARCHAR(1000) NULL,
 created_at DATETIME(3) NOT NULL, stopped_at DATETIME(3) NULL, resolved_at DATETIME(3) NULL,
 KEY ix_change_order(order_id,id), KEY ix_change_warehouse(warehouse_id,status,id),
 FOREIGN KEY(warehouse_id,order_id) REFERENCES outbound_order(warehouse_id,id),
 FOREIGN KEY(assignment_id) REFERENCES order_assignment(id), FOREIGN KEY(requested_by) REFERENCES app_user(id),
 FOREIGN KEY(resolved_by) REFERENCES app_user(id), CHECK(revision>0), CHECK(recovery_epoch>0),
 CHECK(status IN ('REQUESTED','STOPPED','CANCELLED')),
 CHECK(status='REQUESTED' OR stop_receipt IS NOT NULL),
 CHECK(status!='CANCELLED' OR (resolution_receipt IS NOT NULL AND resolved_by IS NOT NULL AND physical_evidence IS NOT NULL))
) ENGINE=InnoDB;
CREATE TABLE recovery_package (
 id BINARY(16) PRIMARY KEY, request_id BINARY(16) NOT NULL UNIQUE,
 payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, byte_count INT NOT NULL,
 actual_count BIGINT NOT NULL, weight_g BIGINT NOT NULL, capture_high_watermark BIGINT NOT NULL,
 audit_through_seq BIGINT NOT NULL, issues_json JSON NOT NULL, received_at DATETIME(3) NOT NULL,
 FOREIGN KEY(request_id) REFERENCES order_change_request(id), CHECK(byte_count>0 AND byte_count<=33554432),
 CHECK(actual_count>=0), CHECK(weight_g>=0), CHECK(capture_high_watermark>=actual_count), CHECK(audit_through_seq>0)
) ENGINE=InnoDB;
ALTER TABLE bale_commitment DROP CHECK bale_commitment_chk_2,
 MODIFY submission_id BINARY(16) NULL, ADD recovery_package_id BINARY(16) NULL,
 ADD FOREIGN KEY(recovery_package_id) REFERENCES recovery_package(id),
 ADD CONSTRAINT ck_commitment_owner CHECK (
  (state IN ('REVIEW_RESERVED','DISPATCHED') AND submission_id IS NOT NULL AND recovery_package_id IS NULL) OR
  (state='RECOVERY_RESERVED' AND submission_id IS NULL AND recovery_package_id IS NOT NULL));
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000037'),'order:recall','申请召回及核验零包作废');
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000038'),'pda:recover','上传本机恢复证据及确认停止');
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p
 WHERE r.code='WAREHOUSE_ADMIN' AND p.code IN ('order:recall','pda:recover');
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p
 WHERE r.code='PDA_OPERATOR' AND p.code='pda:recover';
