SET SESSION lock_wait_timeout=10;

ALTER TABLE temporary_physical_disposition
    ADD close_mode BOOLEAN NOT NULL DEFAULT FALSE,
    ADD close_reason VARCHAR(1000) NULL,
    ADD CONSTRAINT ck_temp_disposition_close
        CHECK ((close_mode=FALSE AND close_reason IS NULL) OR (close_mode=TRUE AND close_reason IS NOT NULL));

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
    ADD CONSTRAINT ck_temp_command_type CHECK(command_type IN ('DUPLICATE_EXCLUDE','LINK','REJECT','SHORT_APPROVE','NORMAL_CONFIRM','PHYSICAL_DISPOSITION','CLOSE_DISPOSITION','REOPEN_WORK'));

ALTER TABLE order_submission_slot
    DROP CHECK ck_slot_phase,
    DROP CHECK ck_slot_current,
    ADD CONSTRAINT ck_slot_phase CHECK(phase IN ('FROZEN','COMPLETED','CONTINUING','CLOSED')),
    ADD CONSTRAINT ck_slot_current CHECK((phase IN ('CONTINUING','CLOSED') AND current_submission_id IS NULL) OR
        (phase IN ('FROZEN','COMPLETED') AND current_submission_id IS NOT NULL));
