SET SESSION lock_wait_timeout=10;

-- Scope-specific preparation authority; never a general temporary_authorization or first-round grant.
CREATE TABLE recovery_recipient_qualification (
    command_id BINARY(16) PRIMARY KEY,
    qualification_id BINARY(16) NOT NULL UNIQUE,
    admission_command_id BINARY(16) NOT NULL UNIQUE,
    warehouse_id BINARY(16) NOT NULL,
    scope_id BINARY(16) NOT NULL,
    case_id BINARY(16) NOT NULL,
    source_recovery_epoch BIGINT NOT NULL,
    target_recovery_epoch BIGINT NOT NULL,
    request_sha256 BINARY(32) NOT NULL,
    request_bytes BLOB NOT NULL,
    issued_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    recorded_at DATETIME(3) NOT NULL,
    FOREIGN KEY(admission_command_id) REFERENCES recovery_recipient_admission(command_id),
    FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),
    FOREIGN KEY(scope_id) REFERENCES submission_scope(id),
    FOREIGN KEY(case_id) REFERENCES handover_case(id),
    CHECK(source_recovery_epoch>0 AND target_recovery_epoch>0 AND source_recovery_epoch<>target_recovery_epoch),
    CHECK(expires_at>issued_at AND recorded_at>=issued_at AND recorded_at<expires_at),
    CHECK(OCTET_LENGTH(request_bytes)>0 AND OCTET_LENGTH(request_bytes)<=16384)
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER recovery_recipient_qualification_insert BEFORE INSERT ON recovery_recipient_qualification FOR EACH ROW
BEGIN
    IF NOT EXISTS(SELECT 1 FROM recovery_recipient_admission a
        JOIN recovery_scope_release r ON r.command_id=a.release_command_id
        JOIN recovery_control_projection p ON p.installation_id=r.installation_id AND p.control_revision=r.control_revision
        JOIN server_state s ON s.id=1 JOIN handover_case h ON h.id=r.case_id
        JOIN submission source ON source.id=h.submission_id
        JOIN temporary_authorization original ON original.id=source.temporary_authorization_id
        JOIN device d ON d.id=a.device_id JOIN app_user u ON u.id=a.user_id
        WHERE a.command_id=NEW.admission_command_id AND NEW.warehouse_id=r.warehouse_id AND NEW.scope_id=r.scope_id
          AND NEW.case_id=r.case_id AND NEW.source_recovery_epoch=original.recovery_epoch
          AND NEW.target_recovery_epoch=r.numeric_epoch AND p.record_sha256=r.control_sha256 AND p.epoch_id=r.epoch_id
          AND p.numeric_epoch=r.numeric_epoch AND s.recovery_epoch=r.numeric_epoch AND s.service_mode='RECOVERY_PENDING'
          AND h.status='APPROVED' AND h.stop_state='FAULT_FENCED' AND h.physical_state='MATCHED'
          AND h.target_device_id IS NULL AND h.target_user_id IS NULL AND source.status='ABORTED'
          AND d.warehouse_id=r.warehouse_id AND d.status='ACTIVE' AND d.row_version=1 AND d.temporary_order_enabled=FALSE
          AND u.status='ACTIVE') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered qualification must match retained recipient and original scope';
    END IF;
    IF UNHEX(SHA2(NEW.request_bytes,256))<>NEW.request_sha256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered qualification signed bytes mismatch';
    END IF;
END$$
CREATE TRIGGER recovery_recipient_qualification_no_update BEFORE UPDATE ON recovery_recipient_qualification FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered qualification is immutable'; END$$
CREATE TRIGGER recovery_recipient_qualification_no_delete BEFORE DELETE ON recovery_recipient_qualification FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered qualification is retained'; END$$
DELIMITER ;
