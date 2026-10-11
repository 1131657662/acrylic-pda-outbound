SET SESSION lock_wait_timeout=10;

-- Retained independent proof of one actual receiving login, not a general work credential.
CREATE TABLE recovery_recipient_session (
    command_id BINARY(16) PRIMARY KEY,
    qualification_id BINARY(16) NOT NULL UNIQUE,
    session_id BINARY(16) NOT NULL UNIQUE,
    challenge_id BINARY(16) NOT NULL UNIQUE,
    case_id BINARY(16) NOT NULL,
    request_sha256 BINARY(32) NOT NULL,
    request_bytes MEDIUMBLOB NOT NULL,
    recorded_at DATETIME(3) NOT NULL,
    FOREIGN KEY(qualification_id) REFERENCES recovery_recipient_qualification(qualification_id),
    FOREIGN KEY(session_id) REFERENCES pda_session(id),
    FOREIGN KEY(challenge_id) REFERENCES device_challenge(id),
    FOREIGN KEY(case_id) REFERENCES handover_case(id),
    CHECK(OCTET_LENGTH(request_bytes)>0 AND OCTET_LENGTH(request_bytes)<=49152)
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER recovery_recipient_session_insert BEFORE INSERT ON recovery_recipient_session FOR EACH ROW
BEGIN
    IF NOT EXISTS(SELECT 1 FROM recovery_recipient_qualification q
        JOIN recovery_recipient_admission a ON a.command_id=q.admission_command_id
        JOIN handover_case h ON h.id=q.case_id JOIN pda_session p ON p.id=NEW.session_id
        JOIN device_challenge c ON c.id=NEW.challenge_id JOIN device d ON d.id=a.device_id
        JOIN app_user u ON u.id=a.user_id JOIN server_state s ON s.id=1
        WHERE q.qualification_id=NEW.qualification_id AND q.case_id=NEW.case_id
          AND q.target_recovery_epoch=s.recovery_epoch AND s.service_mode='RECOVERY_PENDING'
          AND h.status='TARGET_PREPARED' AND h.stop_state='FAULT_FENCED' AND h.physical_state='MATCHED'
          AND h.target_device_id=a.device_id AND h.target_user_id=a.user_id
          AND p.device_id=a.device_id AND p.user_id=a.user_id AND p.revoked_at IS NULL
          AND p.access_expires_at>UTC_TIMESTAMP(3) AND p.expires_at>UTC_TIMESTAMP(3)
          AND p.user_auth_version=u.auth_version AND p.device_version=d.row_version
          AND c.device_id=a.device_id AND c.purpose='LOGIN' AND c.used_at IS NOT NULL
          AND c.used_at>=c.created_at AND c.used_at<c.expires_at
          AND d.status='ACTIVE' AND d.warehouse_id=q.warehouse_id AND d.temporary_order_enabled=FALSE
          AND u.status='ACTIVE' AND q.expires_at>UTC_TIMESTAMP(3)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered session must match actual prepared receiving login';
    END IF;
    IF UNHEX(SHA2(NEW.request_bytes,256))<>NEW.request_sha256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered session signed bytes mismatch';
    END IF;
END$$
CREATE TRIGGER recovery_recipient_session_no_update BEFORE UPDATE ON recovery_recipient_session FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered receiving session is immutable'; END$$
CREATE TRIGGER recovery_recipient_session_no_delete BEFORE DELETE ON recovery_recipient_session FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovered receiving session is retained'; END$$
DELIMITER ;
