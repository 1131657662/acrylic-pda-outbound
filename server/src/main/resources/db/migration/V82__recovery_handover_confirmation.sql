SET SESSION lock_wait_timeout=10;

-- This scoped evidence cannot satisfy the ordinary authorization/activation foreign keys.
CREATE TABLE recovery_handover_confirmation (
    case_id BINARY(16) PRIMARY KEY,
    id BINARY(16) NOT NULL UNIQUE,
    command_id BINARY(16) NOT NULL UNIQUE,
    qualification_id BINARY(16) NOT NULL UNIQUE,
    session_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    device_id BINARY(16) NOT NULL,
    source_recovery_epoch BIGINT NOT NULL,
    target_recovery_epoch BIGINT NOT NULL,
    command_sha256 BINARY(32) NOT NULL,
    command_bytes MEDIUMBLOB NOT NULL,
    receipt_sha256 BINARY(32) NOT NULL,
    receipt_bytes MEDIUMBLOB NOT NULL,
    recorded_at DATETIME(3) NOT NULL,
    request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    FOREIGN KEY(case_id) REFERENCES handover_case(id),
    FOREIGN KEY(qualification_id) REFERENCES recovery_recipient_qualification(qualification_id),
    FOREIGN KEY(session_id) REFERENCES pda_session(id),
    FOREIGN KEY(actor_id) REFERENCES app_user(id),
    FOREIGN KEY(device_id) REFERENCES device(id),
    CHECK(source_recovery_epoch>0 AND target_recovery_epoch>0 AND source_recovery_epoch<>target_recovery_epoch),
    CHECK(OCTET_LENGTH(command_bytes) BETWEEN 1 AND 65536),
    CHECK(OCTET_LENGTH(receipt_bytes) BETWEEN 1 AND 262144),
    CHECK(request_id REGEXP '^[A-Za-z0-9._:-]{1,64}$')
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER recovery_handover_confirmation_insert BEFORE INSERT ON recovery_handover_confirmation FOR EACH ROW
BEGIN
    IF NOT EXISTS(SELECT 1 FROM recovery_recipient_session r
        JOIN recovery_recipient_qualification q ON q.qualification_id=r.qualification_id
        JOIN recovery_recipient_admission a ON a.command_id=q.admission_command_id
        JOIN handover_case h ON h.id=r.case_id JOIN pda_session p ON p.id=r.session_id
        JOIN device d ON d.id=a.device_id JOIN app_user u ON u.id=a.user_id JOIN server_state s ON s.id=1
        WHERE r.case_id=NEW.case_id AND r.qualification_id=NEW.qualification_id AND r.session_id=NEW.session_id
          AND h.target_device_id=NEW.device_id AND h.target_user_id=NEW.actor_id
          AND a.device_id=NEW.device_id AND a.user_id=NEW.actor_id
          AND p.device_id=NEW.device_id AND p.user_id=NEW.actor_id AND p.revoked_at IS NULL
          AND p.access_expires_at>UTC_TIMESTAMP(3) AND p.expires_at>UTC_TIMESTAMP(3)
          AND p.user_auth_version=u.auth_version AND p.device_version=d.row_version
          AND u.status='ACTIVE' AND d.status='ACTIVE' AND d.temporary_order_enabled=FALSE
          AND d.warehouse_id=q.warehouse_id AND h.warehouse_id=q.warehouse_id AND h.scope_id=q.scope_id
          AND h.status='TARGET_PREPARED' AND h.row_version=4 AND h.stop_state='FAULT_FENCED'
          AND h.physical_state='MATCHED' AND h.integrity_state='COMPLETE' AND h.original_result_state='ABORTED'
          AND q.expires_at>NEW.recorded_at AND q.source_recovery_epoch=NEW.source_recovery_epoch
          AND q.target_recovery_epoch=NEW.target_recovery_epoch AND s.recovery_epoch=NEW.target_recovery_epoch
          AND s.service_mode='RECOVERY_PENDING') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered confirmation must match prepared receiving identity';
    END IF;
    IF UNHEX(SHA2(NEW.command_bytes,256))<>NEW.command_sha256 OR UNHEX(SHA2(NEW.receipt_bytes,256))<>NEW.receipt_sha256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered confirmation bytes mismatch';
    END IF;
END$$
CREATE TRIGGER recovery_handover_confirmation_no_update BEFORE UPDATE ON recovery_handover_confirmation FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered confirmation is immutable'; END$$
CREATE TRIGGER recovery_handover_confirmation_no_delete BEFORE DELETE ON recovery_handover_confirmation FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered confirmation is retained'; END$$
DELIMITER ;
