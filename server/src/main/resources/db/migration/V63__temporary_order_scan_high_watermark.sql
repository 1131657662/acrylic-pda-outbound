SET SESSION lock_wait_timeout=10;

SET @temp_receipt_check = (SELECT tc.CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
      ON cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME AND cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA
    WHERE tc.CONSTRAINT_SCHEMA=DATABASE() AND tc.TABLE_NAME='temporary_command_receipt'
      AND cc.CHECK_CLAUSE LIKE '%command_type%' LIMIT 1);
SET @drop_temp_receipt_check = CONCAT('ALTER TABLE temporary_command_receipt DROP CHECK `', @temp_receipt_check, '`');
PREPARE drop_temp_receipt_check FROM @drop_temp_receipt_check;
EXECUTE drop_temp_receipt_check;
DEALLOCATE PREPARE drop_temp_receipt_check;
ALTER TABLE temporary_command_receipt
    ADD CONSTRAINT ck_temp_command_type CHECK(command_type IN ('DUPLICATE_EXCLUDE','LINK','REJECT','SHORT_APPROVE','NORMAL_CONFIRM','PHYSICAL_DISPOSITION','CLOSE_DISPOSITION','REOPEN_WORK','ACTIVATE_CONTINUATION'));

-- The device capture high watermark is a whole-device boundary. A TEMP
-- continuation needs the source order sequence boundary instead. Existing
-- rows cannot be promoted by counting CAPTURE items because that loses the
-- source order sequence and may include other-source captures; they remain
-- NULL and are rejected until a verified source payload is written again.
ALTER TABLE source_evidence_set
    ADD COLUMN order_scan_high_watermark BIGINT NULL;

ALTER TABLE source_evidence_set
    ADD CONSTRAINT ck_evidence_order_scan_high_watermark CHECK(order_scan_high_watermark IS NULL OR order_scan_high_watermark>=0);

-- Ticket consumption is an append-only state machine. Core source facts stay
-- immutable; only the lifecycle fields and the original receipt may advance.
DROP TRIGGER temp_source_continuation_immutable_update;
DELIMITER $$
CREATE TRIGGER temp_source_continuation_state_update
BEFORE UPDATE ON temporary_source_continuation FOR EACH ROW
BEGIN
    IF NEW.id<>OLD.id OR NEW.warehouse_id<>OLD.warehouse_id OR NEW.scope_id<>OLD.scope_id
       OR NEW.previous_submission_id<>OLD.previous_submission_id OR NEW.previous_authorization_id<>OLD.previous_authorization_id
       OR NEW.previous_operator_id<>OLD.previous_operator_id OR NEW.freeze_device_id<>OLD.freeze_device_id
       OR NEW.source_recovery_epoch<>OLD.source_recovery_epoch OR NEW.from_work_round<>OLD.from_work_round
       OR NEW.to_work_round<>OLD.to_work_round OR NEW.evidence_set_id<>OLD.evidence_set_id
       OR NEW.evidence_verification_id<>OLD.evidence_verification_id
       OR NEW.retained_manifest_sha256<>OLD.retained_manifest_sha256 OR NEW.retained_manifest_version<>OLD.retained_manifest_version
       OR NEW.next_order_scan_seq<>OLD.next_order_scan_seq OR NEW.command_receipt_id<>OLD.command_receipt_id
       OR NEW.command_sha256<>OLD.command_sha256 OR NEW.command_text<>OLD.command_text OR NEW.created_at<>OLD.created_at
       OR (OLD.new_authorization_id IS NOT NULL AND (NEW.new_authorization_id IS NULL OR NEW.new_authorization_id<>OLD.new_authorization_id))
    THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation source facts are immutable';
    END IF;
    IF OLD.status='TICKET_ISSUED' AND NEW.status='ACTIVATED' THEN
        IF OLD.new_authorization_id IS NOT NULL OR NEW.new_authorization_id IS NULL OR NEW.receipt_text IS NULL
        THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation activation is incomplete'; END IF;
    ELSEIF OLD.status='ACTIVATED' AND NEW.status IN ('COMPLETED','CANCELLED') THEN
        IF NEW.new_authorization_id IS NULL OR NEW.receipt_text IS NULL
        THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation closure is incomplete'; END IF;
    ELSE SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation status transition is not allowed';
    END IF;
END$$
DELIMITER ;

ALTER TABLE bale_commitment
    ADD COLUMN temporary_continuation_id BINARY(16) NULL,
    ADD KEY ix_bale_temp_continuation(temporary_continuation_id,bale_id),
    ADD CONSTRAINT ck_bale_temp_continuation_fk FOREIGN KEY(temporary_continuation_id)
        REFERENCES temporary_source_continuation(id);

ALTER TABLE bale_commitment DROP CHECK ck_commitment_owner,
    ADD CONSTRAINT ck_commitment_owner CHECK (
      (temporary_continuation_id IS NOT NULL AND state='TEMP_CONTINUATION_RESERVED'
       AND source_scope_id IS NOT NULL AND source_evidence_set_id IS NOT NULL AND submission_id IS NULL
       AND order_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL
       AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL)
      OR (temporary_continuation_id IS NULL AND (
        (source_evidence_set_id IS NULL AND order_id IS NOT NULL AND (
          (state IN ('REVIEW_RESERVED','DISPATCHED') AND submission_id IS NOT NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL) OR
          (state='RECOVERY_RESERVED' AND submission_id IS NULL AND recovery_package_id IS NOT NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL) OR
          (state='CONTINUATION_RESERVED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NOT NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL) OR
          (state='QUARANTINED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NOT NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL) OR
          (state IN ('PC_RESERVED','DISPATCHED') AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NOT NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL) OR
          (state='QUARANTINED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NOT NULL AND disposition_id IS NULL))) OR
        (state='SOURCE_RESERVED' AND source_scope_id IS NOT NULL AND source_evidence_set_id IS NOT NULL AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL) OR
        (state='QUARANTINED' AND source_scope_id IS NOT NULL AND source_evidence_set_id IS NOT NULL AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NOT NULL)
      )));

ALTER TABLE temporary_source_continuation
    ADD UNIQUE KEY uq_temp_continuation_scope_context(scope_id,id,from_work_round);

CREATE TABLE temporary_source_continuation_activation (
    ticket_id BINARY(16) PRIMARY KEY,
    scope_id BINARY(16) NOT NULL,
    from_work_round BIGINT NOT NULL,
    new_authorization_id BINARY(16) NOT NULL,
    new_client_order_id BINARY(16) NOT NULL,
    target_device_id BINARY(16) NOT NULL,
    target_operator_id BINARY(16) NOT NULL,
    command_receipt_id BINARY(16) NOT NULL UNIQUE,
    receipt_text MEDIUMTEXT NOT NULL,
    activated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_temp_continuation_activation_authorization(new_authorization_id),
    UNIQUE KEY uq_temp_continuation_activation_client(new_client_order_id),
    UNIQUE KEY uq_temp_continuation_activation_context(scope_id,ticket_id,from_work_round),
    FOREIGN KEY(ticket_id) REFERENCES temporary_source_continuation(id),
    FOREIGN KEY(scope_id,ticket_id,from_work_round) REFERENCES temporary_source_continuation(scope_id,id,from_work_round),
    FOREIGN KEY(new_authorization_id) REFERENCES temporary_authorization(id),
    FOREIGN KEY(target_device_id,new_client_order_id) REFERENCES temporary_work_origin(creator_device_id,client_order_id),
    FOREIGN KEY(target_device_id) REFERENCES device(id),
    FOREIGN KEY(target_operator_id) REFERENCES app_user(id),
    CHECK(from_work_round>0), CHECK(JSON_VALID(receipt_text))
) ENGINE=InnoDB;
