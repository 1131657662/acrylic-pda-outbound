SET SESSION lock_wait_timeout=10;

CREATE TABLE temporary_physical_disposition (
 id BINARY(16) PRIMARY KEY,
 warehouse_id BINARY(16) NOT NULL,
 scope_id BINARY(16) NOT NULL,
 submission_id BINARY(16) NOT NULL,
 work_round BIGINT NOT NULL,
 evidence_set_id BINARY(16) NOT NULL,
 evidence_verification_id BINARY(16) NOT NULL,
 manifest_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 manifest_version BIGINT NOT NULL,
 recorded_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_temp_disposition_submission(submission_id,work_round),
 UNIQUE KEY uq_temp_disposition_evidence(evidence_verification_id),
 FOREIGN KEY(warehouse_id,scope_id) REFERENCES submission_scope(warehouse_id,id),
 FOREIGN KEY(scope_id,submission_id,work_round) REFERENCES submission(scope_id,id,work_round),
 FOREIGN KEY(evidence_set_id,scope_id,submission_id,work_round) REFERENCES source_evidence_set(id,scope_id,submission_id,work_round),
 FOREIGN KEY(evidence_verification_id,evidence_set_id,scope_id) REFERENCES source_evidence_verification(id,set_id,scope_id),
 CHECK(work_round>0), CHECK(manifest_version>0)
) ENGINE=InnoDB;

CREATE TABLE temporary_physical_disposition_item (
 disposition_id BINARY(16) NOT NULL,
 bale_id BINARY(16) NOT NULL,
 dispatch_cycle BIGINT NOT NULL,
 disposition VARCHAR(32) NOT NULL,
 PRIMARY KEY(disposition_id,bale_id),
 UNIQUE KEY uq_temp_disposition_bale(bale_id),
 FOREIGN KEY(disposition_id) REFERENCES temporary_physical_disposition(id),
 FOREIGN KEY(bale_id) REFERENCES bale(id),
 CHECK(dispatch_cycle>0), CHECK(disposition='PHYSICAL_DISPOSED')
) ENGINE=InnoDB;

ALTER TABLE bale_commitment
 DROP CHECK ck_commitment_owner,
 ADD disposition_id BINARY(16) NULL,
 ADD FOREIGN KEY(disposition_id) REFERENCES temporary_physical_disposition(id),
 ADD CONSTRAINT ck_commitment_owner CHECK (
 (source_evidence_set_id IS NULL AND order_id IS NOT NULL AND (
 (state IN ('REVIEW_RESERVED','DISPATCHED') AND submission_id IS NOT NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL) OR
 (state='RECOVERY_RESERVED' AND submission_id IS NULL AND recovery_package_id IS NOT NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL) OR
 (state='CONTINUATION_RESERVED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NOT NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL) OR
 (state='QUARANTINED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NOT NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL) OR
 (state IN ('PC_RESERVED','DISPATCHED') AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NOT NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL) OR
 (state='QUARANTINED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NOT NULL AND disposition_id IS NULL))) OR
 (state='SOURCE_RESERVED' AND source_scope_id IS NOT NULL AND source_evidence_set_id IS NOT NULL AND submission_id IS NULL AND recovery_package_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL) OR
 (state='QUARANTINED' AND source_scope_id IS NOT NULL AND source_evidence_set_id IS NOT NULL AND submission_id IS NULL AND recovery_package_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NOT NULL));
