SET SESSION lock_wait_timeout=10;
CREATE TABLE order_continuation (
 id BINARY(16) PRIMARY KEY, request_id BINARY(16) NOT NULL UNIQUE, scope_id BINARY(16) NOT NULL,
 order_id BINARY(16) NOT NULL, previous_submission_id BINARY(16) NOT NULL UNIQUE,
 from_round BIGINT NOT NULL, to_round BIGINT NOT NULL, revision INT NOT NULL,
 assigned_device_id BINARY(16) NOT NULL, recovery_package_id BINARY(16) NOT NULL UNIQUE,
 retained_manifest_sha256 BINARY(32) NOT NULL, next_order_scan_seq BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL, command_text MEDIUMTEXT NOT NULL, requested_by BINARY(16) NOT NULL,
 new_assignment_id BINARY(16) NULL UNIQUE, approved_by BINARY(16) NULL, physical_evidence VARCHAR(1000) NULL,
 receipt_text MEDIUMTEXT NULL, activation_text MEDIUMTEXT NULL,
 created_at DATETIME(3) NOT NULL, approved_at DATETIME(3) NULL, activated_at DATETIME(3) NULL,
 UNIQUE KEY uq_continuation_round(scope_id,to_round),
 FOREIGN KEY(request_id) REFERENCES order_change_request(id), FOREIGN KEY(scope_id) REFERENCES submission_scope(id),
 FOREIGN KEY(order_id) REFERENCES outbound_order(id), FOREIGN KEY(previous_submission_id) REFERENCES submission(id),
 FOREIGN KEY(assigned_device_id) REFERENCES device(id), FOREIGN KEY(recovery_package_id) REFERENCES recovery_package(id),
 FOREIGN KEY(new_assignment_id) REFERENCES order_assignment(id), FOREIGN KEY(requested_by) REFERENCES app_user(id),
 FOREIGN KEY(approved_by) REFERENCES app_user(id),
 CHECK(from_round>0 AND to_round=from_round+1), CHECK(revision>0), CHECK(next_order_scan_seq>0),
 CHECK(status IN ('REQUESTED','APPROVED','ACTIVATED','COMPLETED')),
 CHECK(status='REQUESTED' OR (new_assignment_id IS NOT NULL AND approved_by IS NOT NULL AND physical_evidence IS NOT NULL AND receipt_text IS NOT NULL))
) ENGINE=InnoDB;
CREATE TABLE continuation_bale (
 continuation_id BINARY(16) NOT NULL, bale_id BINARY(16) NOT NULL, origin_scan_id BINARY(16) NOT NULL,
 expected_cycle BIGINT NOT NULL, record_sha256 BINARY(32) NOT NULL,
 PRIMARY KEY(continuation_id,bale_id), UNIQUE KEY uq_continuation_scan(continuation_id,origin_scan_id),
 FOREIGN KEY(continuation_id) REFERENCES order_continuation(id), FOREIGN KEY(bale_id) REFERENCES bale(id), CHECK(expected_cycle>0)
) ENGINE=InnoDB;
ALTER TABLE order_submission_slot DROP CHECK order_submission_slot_chk_2,
 MODIFY current_submission_id BINARY(16) NULL,
 ADD CONSTRAINT ck_slot_phase CHECK(phase IN ('FROZEN','COMPLETED','CONTINUING')),
 ADD CONSTRAINT ck_slot_current CHECK((phase='CONTINUING' AND current_submission_id IS NULL) OR (phase!='CONTINUING' AND current_submission_id IS NOT NULL));
ALTER TABLE submission ADD continuation_id BINARY(16) NULL, ADD superseded_by BINARY(16) NULL,
 ADD FOREIGN KEY(continuation_id) REFERENCES order_continuation(id), ADD FOREIGN KEY(superseded_by) REFERENCES order_continuation(id);
ALTER TABLE bale_commitment DROP CHECK ck_commitment_owner, ADD continuation_id BINARY(16) NULL,
 ADD FOREIGN KEY(continuation_id) REFERENCES order_continuation(id),
 ADD CONSTRAINT ck_commitment_owner CHECK (
 (state IN ('REVIEW_RESERVED','DISPATCHED') AND submission_id IS NOT NULL AND recovery_package_id IS NULL AND continuation_id IS NULL) OR
 (state='RECOVERY_RESERVED' AND submission_id IS NULL AND recovery_package_id IS NOT NULL AND continuation_id IS NULL) OR
 (state='CONTINUATION_RESERVED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NOT NULL));
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000039'),'order:resume','审批原人原机全量保留续作');
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p WHERE r.code='WAREHOUSE_ADMIN' AND p.code='order:resume';
