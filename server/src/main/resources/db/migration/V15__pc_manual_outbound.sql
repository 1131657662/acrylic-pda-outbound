SET SESSION lock_wait_timeout=10;
CREATE TABLE pc_work (
 id BINARY(16) PRIMARY KEY, warehouse_id BINARY(16) NOT NULL, order_id BINARY(16) NOT NULL UNIQUE,
 revision INT NOT NULL, publication_id BINARY(16) NOT NULL, operator_id BINARY(16) NOT NULL,
 recovery_epoch BIGINT NOT NULL, status VARCHAR(24) NOT NULL, row_version BIGINT NOT NULL DEFAULT 1,
 writer_id BINARY(16) NOT NULL, writer_fence BIGINT NOT NULL DEFAULT 1,
 next_sequence BIGINT NOT NULL DEFAULT 1, work_round BIGINT NOT NULL DEFAULT 1,
 created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 FOREIGN KEY(warehouse_id,order_id) REFERENCES outbound_order(warehouse_id,id),
 FOREIGN KEY(publication_id) REFERENCES order_publication(id), FOREIGN KEY(operator_id) REFERENCES app_user(id),
 CHECK(revision>0 AND recovery_epoch>0 AND row_version>0 AND writer_fence>0 AND next_sequence>0 AND work_round>0),
 CHECK(status IN ('ACTIVE','REVIEW_REQUIRED','REJECTED','SUCCEEDED'))
) ENGINE=InnoDB;
CREATE TABLE pc_entry (
 id BINARY(16) PRIMARY KEY, work_id BINARY(16) NOT NULL, bale_id BINARY(16) NOT NULL,
 sequence_no BIGINT NOT NULL, dispatch_cycle BIGINT NOT NULL, status VARCHAR(12) NOT NULL,
 active_bale BINARY(16) GENERATED ALWAYS AS (IF(status='ACTIVE',bale_id,NULL)) STORED,
 delivery_code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 weight_g BIGINT NOT NULL, raw_weight VARCHAR(20) NOT NULL, model_snapshot VARCHAR(100) NOT NULL, name_snapshot VARCHAR(100) NOT NULL,
 created_by BINARY(16) NOT NULL, operator_name VARCHAR(100) NOT NULL, created_at DATETIME(3) NOT NULL, voided_by BINARY(16) NULL,
 voided_at DATETIME(3) NULL, void_reason VARCHAR(500) NULL,
 UNIQUE KEY uq_pc_entry_sequence(work_id,sequence_no), UNIQUE KEY uq_pc_active_bale(work_id,active_bale),
 KEY ix_pc_entry_product(work_id,delivery_code,status,sequence_no),
 FOREIGN KEY(work_id) REFERENCES pc_work(id), FOREIGN KEY(bale_id) REFERENCES bale(id),
 FOREIGN KEY(created_by) REFERENCES app_user(id), FOREIGN KEY(voided_by) REFERENCES app_user(id),
 CHECK(sequence_no>0 AND dispatch_cycle>0 AND weight_g>0), CHECK(status IN ('ACTIVE','VOIDED')),
 CHECK((status='ACTIVE' AND voided_at IS NULL AND voided_by IS NULL AND void_reason IS NULL) OR
       (status='VOIDED' AND voided_at IS NOT NULL AND voided_by IS NOT NULL AND LENGTH(void_reason)>0))
) ENGINE=InnoDB;
CREATE TABLE pc_submission (
 id BINARY(16) PRIMARY KEY, work_id BINARY(16) NOT NULL, work_round BIGINT NOT NULL,
 status VARCHAR(24) NOT NULL, payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 payload_text LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 actual_count BIGINT NOT NULL, weight_g BIGINT NOT NULL, short_reason VARCHAR(500) NOT NULL,
 issues_json JSON NOT NULL, receipt_json JSON NULL, created_by BINARY(16) NOT NULL, created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_pc_submission_round(work_id,work_round), FOREIGN KEY(work_id) REFERENCES pc_work(id),
 FOREIGN KEY(created_by) REFERENCES app_user(id), CHECK(work_round>0 AND actual_count>0 AND weight_g>0),
 CHECK(status IN ('REVIEW_REQUIRED','REJECTED','SUCCEEDED')), CHECK(status!='SUCCEEDED' OR receipt_json IS NOT NULL)
) ENGINE=InnoDB;
CREATE TABLE pc_review (
 submission_id BINARY(16) PRIMARY KEY, decision VARCHAR(12) NOT NULL, reason VARCHAR(500) NOT NULL,
 actor_id BINARY(16) NOT NULL, permission_version BIGINT NOT NULL, created_at DATETIME(3) NOT NULL,
 FOREIGN KEY(submission_id) REFERENCES pc_submission(id), FOREIGN KEY(actor_id) REFERENCES app_user(id),
 CHECK(decision IN ('APPROVE','REJECT')), CHECK(LENGTH(reason)>0)
) ENGINE=InnoDB;
CREATE TABLE pc_command_receipt (
 id BINARY(16) PRIMARY KEY, order_id BINARY(16) NOT NULL, body_json JSON NOT NULL, created_at DATETIME(3) NOT NULL,
 FOREIGN KEY(order_id) REFERENCES outbound_order(id)
) ENGINE=InnoDB;
ALTER TABLE bale_commitment DROP CHECK ck_commitment_owner, ADD pc_work_id BINARY(16) NULL,
 ADD FOREIGN KEY(pc_work_id) REFERENCES pc_work(id),
 ADD CONSTRAINT ck_commitment_owner CHECK (
 (state IN ('REVIEW_RESERVED','DISPATCHED') AND submission_id IS NOT NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL) OR
 (state='RECOVERY_RESERVED' AND submission_id IS NULL AND recovery_package_id IS NOT NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL) OR
 (state='CONTINUATION_RESERVED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NOT NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL) OR
 (state='QUARANTINED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NOT NULL AND pc_work_id IS NULL) OR
 (state IN ('PC_RESERVED','DISPATCHED') AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NOT NULL));
ALTER TABLE dispatch_record MODIFY submission_id BINARY(16) NULL, MODIFY capture_id BINARY(16) NULL,
 ADD pc_submission_id BINARY(16) NULL, ADD pc_entry_id BINARY(16) NULL UNIQUE,
 ADD FOREIGN KEY(pc_submission_id) REFERENCES pc_submission(id), ADD FOREIGN KEY(pc_entry_id) REFERENCES pc_entry(id),
 ADD CONSTRAINT ck_dispatch_channel CHECK (
 (submission_id IS NOT NULL AND capture_id IS NOT NULL AND pc_submission_id IS NULL AND pc_entry_id IS NULL) OR
 (submission_id IS NULL AND capture_id IS NULL AND pc_submission_id IS NOT NULL AND pc_entry_id IS NOT NULL));
ALTER TABLE order_result_version MODIFY submission_id BINARY(16) NULL, ADD pc_submission_id BINARY(16) NULL UNIQUE,
 ADD FOREIGN KEY(pc_submission_id) REFERENCES pc_submission(id),
 ADD CONSTRAINT ck_result_channel CHECK ((submission_id IS NOT NULL AND pc_submission_id IS NULL) OR (submission_id IS NULL AND pc_submission_id IS NOT NULL));
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000041'),'pc:operate','开始及继续PC手工出库');
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p WHERE r.code='WAREHOUSE_ADMIN' AND p.code='pc:operate';
