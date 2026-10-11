SET SESSION lock_wait_timeout=10;

-- Explicit recovered enrollment only; ordinary TEMP work remains disabled for the new recipient.
CREATE TABLE recovery_recipient_admission (
    command_id BINARY(16) PRIMARY KEY,
    release_command_id BINARY(16) NOT NULL UNIQUE,
    device_id BINARY(16) NOT NULL UNIQUE,
    user_id BINARY(16) NOT NULL,
    request_sha256 BINARY(32) NOT NULL,
    request_bytes BLOB NOT NULL,
    recorded_at DATETIME(3) NOT NULL,
    KEY ix_recovery_recipient_user(user_id),
    FOREIGN KEY(release_command_id) REFERENCES recovery_scope_release(command_id),
    FOREIGN KEY(device_id) REFERENCES device(id),
    FOREIGN KEY(user_id) REFERENCES app_user(id),
    CHECK(OCTET_LENGTH(request_bytes)>0 AND OCTET_LENGTH(request_bytes)<=16384)
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER recovery_recipient_admission_insert BEFORE INSERT ON recovery_recipient_admission FOR EACH ROW
BEGIN
    IF NOT EXISTS(SELECT 1 FROM recovery_scope_release r
        JOIN recovery_control_projection p ON p.installation_id=r.installation_id AND p.control_revision=r.control_revision
        JOIN server_state s ON s.id=1 JOIN handover_case h ON h.id=r.case_id
        JOIN handover_fault_fence f ON f.case_id=h.id
        JOIN device d ON d.id=NEW.device_id JOIN app_user u ON u.id=NEW.user_id
        WHERE r.command_id=NEW.release_command_id AND p.record_sha256=r.control_sha256 AND p.epoch_id=r.epoch_id
          AND p.numeric_epoch=r.numeric_epoch AND s.recovery_epoch=r.numeric_epoch AND s.service_mode='RECOVERY_PENDING'
          AND h.status='APPROVED' AND h.stop_state='FAULT_FENCED' AND h.physical_state='MATCHED'
          AND d.id<>f.source_device_id AND d.warehouse_id=r.warehouse_id AND d.status='ACTIVE'
          AND d.row_version=1 AND d.temporary_order_enabled=FALSE AND d.last_seen_at IS NULL AND u.status='ACTIVE') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered recipient must match closed approved scope';
    END IF;
    IF UNHEX(SHA2(NEW.request_bytes,256))<>NEW.request_sha256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered recipient signed bytes mismatch';
    END IF;
END$$
CREATE TRIGGER recovery_recipient_admission_no_update BEFORE UPDATE ON recovery_recipient_admission FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered recipient admission is immutable'; END$$
CREATE TRIGGER recovery_recipient_admission_no_delete BEFORE DELETE ON recovery_recipient_admission FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered recipient admission is retained'; END$$
DELIMITER ;
