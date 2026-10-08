SET SESSION lock_wait_timeout=10;

-- A fault fence records supervisor control and pre-existing origin evidence, never a device acknowledgement.
CREATE TABLE handover_fault_fence (
    case_id BINARY(16) PRIMARY KEY,
    warehouse_id BINARY(16) NOT NULL,
    scope_id BINARY(16) NOT NULL,
    submission_id BINARY(16) NOT NULL,
    source_device_id BINARY(16) NOT NULL,
    device_version BIGINT NOT NULL,
    source_evidence_set_id BINARY(16) NOT NULL,
    verification_id BINARY(16) NOT NULL,
    command_key BINARY(16) NOT NULL UNIQUE,
    command_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_high_watermark BIGINT NOT NULL,
    recorded_by BINARY(16) NOT NULL,
    recorded_at DATETIME(3) NOT NULL,
    KEY ix_handover_fault_device(source_device_id),
    FOREIGN KEY(case_id) REFERENCES handover_case(id),
    FOREIGN KEY(warehouse_id,scope_id) REFERENCES submission_scope(warehouse_id,id),
    FOREIGN KEY(submission_id) REFERENCES submission(id),
    FOREIGN KEY(source_device_id) REFERENCES device(id),
    FOREIGN KEY(verification_id,source_evidence_set_id,scope_id) REFERENCES source_evidence_verification(id,set_id,scope_id),
    FOREIGN KEY(recorded_by) REFERENCES app_user(id),
    CHECK(device_version>0 AND input_high_watermark>=0),
    CHECK(command_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB;

-- Discover the original unnamed checks rather than assuming MySQL's generated ordinal.
SET @fault_activation_check=(SELECT tc.CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME
    WHERE tc.TABLE_SCHEMA=DATABASE() AND tc.TABLE_NAME='handover_case' AND tc.CONSTRAINT_TYPE='CHECK'
      AND cc.CHECK_CLAUSE LIKE '%activation_sha256%' AND cc.CHECK_CLAUSE LIKE '%stop_state%');
SET @fault_activation_sql=CONCAT('ALTER TABLE handover_case DROP CHECK `',@fault_activation_check,'`, ADD CONSTRAINT ck_handover_activation_evidence CHECK (status <> ''ACTIVATED'' OR (stop_state IN (''DEVICE_ACKED'',''FAULT_FENCED'') AND integrity_state=''COMPLETE'' AND physical_state=''MATCHED'' AND original_result_state IN (''NONE_VERIFIED'',''ABORTED'',''REJECTED'') AND activation_sha256 IS NOT NULL AND input_high_watermark IS NOT NULL))');
PREPARE fault_activation_statement FROM @fault_activation_sql;
EXECUTE fault_activation_statement;
DEALLOCATE PREPARE fault_activation_statement;

SET @fault_command_check=(SELECT tc.CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME
    WHERE tc.TABLE_SCHEMA=DATABASE() AND tc.TABLE_NAME='handover_command_receipt' AND tc.CONSTRAINT_TYPE='CHECK'
      AND cc.CHECK_CLAUSE LIKE '%command_type%');
SET @fault_command_sql=CONCAT('ALTER TABLE handover_command_receipt DROP CHECK `',@fault_command_check,'`, ADD CONSTRAINT ck_handover_command_kind CHECK(command_type IN (''REQUEST'',''APPROVE'',''PREPARE'',''ACTIVATE'',''FAULT_FENCE''))');
PREPARE fault_command_statement FROM @fault_command_sql;
EXECUTE fault_command_statement;
DEALLOCATE PREPARE fault_command_statement;

DELIMITER $$
CREATE TRIGGER handover_fault_no_update BEFORE UPDATE ON handover_fault_fence FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fault fence is immutable'; END$$
CREATE TRIGGER handover_fault_no_delete BEFORE DELETE ON handover_fault_fence FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fault fence is retained'; END$$
CREATE TRIGGER device_fault_no_reenable BEFORE UPDATE ON device FOR EACH ROW
BEGIN
    IF EXISTS(SELECT 1 FROM handover_fault_fence WHERE source_device_id=OLD.id)
       AND (NEW.status<>'DISABLED' OR NOT(NEW.public_key<=>OLD.public_key) OR NOT(NEW.key_sha256<=>OLD.key_sha256)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fault device requires dedicated recovery; ordinary re-enable is forbidden';
    END IF;
END$$
CREATE TRIGGER handover_fault_case_binding BEFORE UPDATE ON handover_case FOR EACH ROW
BEGIN
    IF NEW.stop_state='FAULT_FENCED' AND NOT EXISTS(
        SELECT 1 FROM handover_fault_fence f JOIN device d ON d.id=f.source_device_id
        WHERE f.case_id=NEW.id AND f.warehouse_id=NEW.warehouse_id AND f.scope_id=NEW.scope_id
          AND f.submission_id=NEW.submission_id AND f.input_high_watermark=NEW.input_high_watermark
          AND d.status='DISABLED' AND d.row_version>=f.device_version) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fault state requires durable device fence and original evidence';
    END IF;
    IF OLD.stop_state='FAULT_FENCED' AND NEW.stop_state<>'FAULT_FENCED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fault fence cannot become a device acknowledgement';
    END IF;
END$$
DELIMITER ;
