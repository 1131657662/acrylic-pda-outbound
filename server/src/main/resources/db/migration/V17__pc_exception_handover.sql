SET SESSION lock_wait_timeout=10;
ALTER TABLE pc_work DROP CHECK pc_work_chk_2,
 ADD CONSTRAINT ck_pc_work_status CHECK(status IN ('ACTIVE','REVIEW_REQUIRED','REJECTED','SUCCEEDED','STOPPED','CANCELLED'));
ALTER TABLE pc_submission DROP CHECK pc_submission_chk_2,
 ADD CONSTRAINT ck_pc_submission_status CHECK(status IN ('REVIEW_REQUIRED','REJECTED','SUCCEEDED','ABORTED'));
ALTER TABLE pc_submission_entry DROP CHECK pc_submission_entry_chk_1,
 ADD CONSTRAINT ck_pc_frozen_status CHECK(frozen_status IN ('ACTIVE','VOIDED','RELEASED','QUARANTINED'));
CREATE TABLE pc_exception_case (
 id BINARY(16) PRIMARY KEY, work_id BINARY(16) NOT NULL, work_round BIGINT NOT NULL,
 previous_operator_id BINARY(16) NOT NULL, status VARCHAR(16) NOT NULL,
 open_work BINARY(16) GENERATED ALWAYS AS (IF(status='STOPPED',work_id,NULL)) STORED,
 snapshot_text LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 snapshot_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reason VARCHAR(1000) NOT NULL, stopped_by BINARY(16) NOT NULL, stopped_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_pc_open_exception(open_work), UNIQUE KEY uq_pc_exception_round(work_id,work_round),
 FOREIGN KEY(work_id) REFERENCES pc_work(id), FOREIGN KEY(previous_operator_id) REFERENCES app_user(id),
 FOREIGN KEY(stopped_by) REFERENCES app_user(id), CHECK(status IN ('STOPPED','APPLIED')), CHECK(work_round>0)
) ENGINE=InnoDB;
CREATE TABLE pc_exception_plan (
 id BINARY(16) PRIMARY KEY, case_id BINARY(16) NOT NULL, target_user_id BINARY(16) NOT NULL,
 outcome VARCHAR(16) NOT NULL, status VARCHAR(16) NOT NULL, reason VARCHAR(1000) NOT NULL,
 payload_text LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 requested_by BINARY(16) NOT NULL, requested_at DATETIME(3) NOT NULL,
 approved_by BINARY(16) NULL, applied_by BINARY(16) NULL, applied_at DATETIME(3) NULL,
 handover_evidence VARCHAR(1000) NULL, KEY ix_pc_plan_case(case_id,id),
 FOREIGN KEY(case_id) REFERENCES pc_exception_case(id), FOREIGN KEY(target_user_id) REFERENCES app_user(id),
 FOREIGN KEY(requested_by) REFERENCES app_user(id), FOREIGN KEY(approved_by) REFERENCES app_user(id),
 FOREIGN KEY(applied_by) REFERENCES app_user(id), CHECK(outcome IN ('CONTINUE','CANCEL')),
 CHECK(status IN ('PENDING','APPROVED','REJECTED','APPLIED')),
 CHECK(status NOT IN ('APPROVED','APPLIED') OR approved_by IS NOT NULL),
 CHECK(status!='APPLIED' OR (applied_by IS NOT NULL AND applied_at IS NOT NULL AND handover_evidence IS NOT NULL))
) ENGINE=InnoDB;
CREATE TABLE pc_exception_review (
 id BINARY(16) PRIMARY KEY, plan_id BINARY(16) NOT NULL, decision VARCHAR(16) NOT NULL,
 reason VARCHAR(1000) NOT NULL, actor_id BINARY(16) NOT NULL, permission_version BIGINT NOT NULL, created_at DATETIME(3) NOT NULL,
 FOREIGN KEY(plan_id) REFERENCES pc_exception_plan(id), FOREIGN KEY(actor_id) REFERENCES app_user(id),
 CHECK(decision IN ('APPROVE','REJECT'))
) ENGINE=InnoDB;
CREATE TABLE pc_disposition (
 id BINARY(16) PRIMARY KEY, plan_id BINARY(16) NOT NULL, entry_id BINARY(16) NOT NULL UNIQUE,
 action VARCHAR(16) NOT NULL, expected_cycle BIGINT NOT NULL,
 record_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 physical_evidence VARCHAR(1000) NOT NULL, applied_at DATETIME(3) NOT NULL,
 FOREIGN KEY(plan_id) REFERENCES pc_exception_plan(id), FOREIGN KEY(entry_id) REFERENCES pc_entry(id),
 CHECK(action IN ('RELEASE','QUARANTINE')), CHECK(expected_cycle>0)
) ENGINE=InnoDB;
CREATE TABLE pc_retained_entry (
 plan_id BINARY(16) NOT NULL, entry_id BINARY(16) NOT NULL,
 PRIMARY KEY(plan_id,entry_id), KEY ix_pc_retained_entry(entry_id),
 FOREIGN KEY(plan_id) REFERENCES pc_exception_plan(id), FOREIGN KEY(entry_id) REFERENCES pc_entry(id)
) ENGINE=InnoDB;
ALTER TABLE pc_entry ADD disposition_id BINARY(16) NULL UNIQUE,
 ADD FOREIGN KEY(disposition_id) REFERENCES pc_disposition(id),
 DROP INDEX uq_pc_active_bale,
 MODIFY active_bale BINARY(16) GENERATED ALWAYS AS (IF(status='ACTIVE' AND disposition_id IS NULL,bale_id,NULL)) STORED,
 ADD UNIQUE KEY uq_pc_active_bale(work_id,active_bale);
ALTER TABLE bale_commitment DROP CHECK ck_commitment_owner, ADD pc_disposition_id BINARY(16) NULL,
 ADD FOREIGN KEY(pc_disposition_id) REFERENCES pc_disposition(id),
 ADD CONSTRAINT ck_commitment_owner CHECK (
 (state IN ('REVIEW_RESERVED','DISPATCHED') AND submission_id IS NOT NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL) OR
 (state='RECOVERY_RESERVED' AND submission_id IS NULL AND recovery_package_id IS NOT NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL) OR
 (state='CONTINUATION_RESERVED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NOT NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL) OR
 (state='QUARANTINED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NOT NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL) OR
 (state IN ('PC_RESERVED','DISPATCHED') AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NOT NULL AND pc_disposition_id IS NULL) OR
 (state='QUARANTINED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NOT NULL));
