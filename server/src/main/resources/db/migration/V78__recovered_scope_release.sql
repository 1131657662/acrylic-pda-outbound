SET SESSION lock_wait_timeout=10;

-- Explicit recovered TEMP scope metadata only. No source facts, stock or ordinary-write mode changes.
CREATE TABLE recovery_scope_release (
    command_id BINARY(16) PRIMARY KEY,
    installation_id BINARY(16) NOT NULL,
    control_revision BIGINT NOT NULL,
    control_sha256 BINARY(32) NOT NULL,
    epoch_id BINARY(16) NOT NULL,
    numeric_epoch BIGINT NOT NULL,
    warehouse_id BINARY(16) NOT NULL,
    scope_id BINARY(16) NOT NULL,
    case_id BINARY(16) NOT NULL,
    authority_sha256 BINARY(32) NOT NULL,
    approval_sha256 BINARY(32) NOT NULL,
    prepare_sha256 BINARY(32) NOT NULL,
    commit_sha256 BINARY(32) NOT NULL,
    commit_bytes BLOB NOT NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_recovery_scope_epoch(installation_id,epoch_id,scope_id),
    UNIQUE KEY uq_recovery_scope_prepare(prepare_sha256),
    FOREIGN KEY(installation_id,control_revision) REFERENCES recovery_control_projection(installation_id,control_revision),
    FOREIGN KEY(installation_id,epoch_id,numeric_epoch) REFERENCES recovery_control_epoch(installation_id,epoch_id,numeric_epoch),
    FOREIGN KEY(warehouse_id,scope_id) REFERENCES submission_scope(warehouse_id,id),
    FOREIGN KEY(case_id) REFERENCES handover_case(id),
    CHECK(control_revision>0 AND numeric_epoch>0),
    CHECK(OCTET_LENGTH(commit_bytes)>0 AND OCTET_LENGTH(commit_bytes)<=16384)
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER recovery_scope_release_insert BEFORE INSERT ON recovery_scope_release FOR EACH ROW
BEGIN
    IF NOT EXISTS(SELECT 1 FROM recovery_control_projection p
        JOIN server_state s ON s.id=1 JOIN handover_case h ON h.id=NEW.case_id
        WHERE p.installation_id=NEW.installation_id AND p.control_revision=NEW.control_revision
          AND p.record_sha256=NEW.control_sha256 AND p.epoch_id=NEW.epoch_id AND p.numeric_epoch=NEW.numeric_epoch
          AND s.recovery_epoch=NEW.numeric_epoch AND s.service_mode='RECOVERY_PENDING'
          AND h.warehouse_id=NEW.warehouse_id AND h.scope_id=NEW.scope_id AND h.source_kind='TEMP'
          AND h.status='APPROVED' AND h.stop_state='FAULT_FENCED' AND h.integrity_state='COMPLETE'
          AND h.physical_state='MATCHED' AND h.original_result_state='ABORTED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered scope must match closed control and approved physical case';
    END IF;
    IF UNHEX(SHA2(NEW.commit_bytes,256))<>NEW.commit_sha256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered scope commit bytes mismatch';
    END IF;
END$$
CREATE TRIGGER recovery_scope_release_no_update BEFORE UPDATE ON recovery_scope_release FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered scope release is immutable'; END$$
CREATE TRIGGER recovery_scope_release_no_delete BEFORE DELETE ON recovery_scope_release FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered scope release is retained'; END$$
DELIMITER ;
