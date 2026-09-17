SET SESSION lock_wait_timeout=10;
CREATE TABLE physical_disposition_evidence (
 id BINARY(16) PRIMARY KEY, approval_batch_id BINARY(16) NOT NULL, recovery_package_id BINARY(16) NOT NULL, order_id BINARY(16) NOT NULL,
 bale_id BINARY(16) NOT NULL, origin_scan_id BINARY(16) NOT NULL, expected_cycle BIGINT NOT NULL,
 action VARCHAR(16) NOT NULL, record_sha256 BINARY(32) NOT NULL, recovery_epoch BIGINT NOT NULL,
 physical_evidence VARCHAR(1000) NOT NULL, approved_by BINARY(16) NOT NULL, approved_at DATETIME(3) NOT NULL,
 applied_submission_id BINARY(16) NULL,
 UNIQUE KEY uq_disposition_recovery_scan(recovery_package_id,origin_scan_id),
 KEY ix_disposition_batch(approval_batch_id,id),
 FOREIGN KEY(recovery_package_id) REFERENCES recovery_package(id), FOREIGN KEY(order_id) REFERENCES outbound_order(id),
 FOREIGN KEY(bale_id) REFERENCES bale(id), FOREIGN KEY(approved_by) REFERENCES app_user(id),
 FOREIGN KEY(applied_submission_id) REFERENCES submission(id),
 CHECK(expected_cycle>0), CHECK(action IN ('RELEASE','QUARANTINE')), CHECK(recovery_epoch>0)
) ENGINE=InnoDB;
ALTER TABLE bale_commitment DROP CHECK ck_commitment_owner, ADD disposition_evidence_id BINARY(16) NULL,
 ADD FOREIGN KEY(disposition_evidence_id) REFERENCES physical_disposition_evidence(id),
 ADD CONSTRAINT ck_commitment_owner CHECK (
 (state IN ('REVIEW_RESERVED','DISPATCHED') AND submission_id IS NOT NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL) OR
 (state='RECOVERY_RESERVED' AND submission_id IS NULL AND recovery_package_id IS NOT NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL) OR
 (state='CONTINUATION_RESERVED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NOT NULL AND disposition_evidence_id IS NULL) OR
 (state='QUARANTINED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NOT NULL));
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000040'),'order:dispose','批准卸货释放或实物隔离凭据');
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p WHERE r.code='WAREHOUSE_ADMIN' AND p.code='order:dispose';
