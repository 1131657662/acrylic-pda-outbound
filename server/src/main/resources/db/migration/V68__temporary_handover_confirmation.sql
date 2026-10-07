SET SESSION lock_wait_timeout=10;

-- Recipient confirmation is evidence only; activation and stock ownership do not change here.
CREATE TABLE temporary_handover_confirmation (
    case_id BINARY(16) PRIMARY KEY,
    id BINARY(16) NOT NULL,
    command_id BINARY(16) NOT NULL,
    authorization_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    device_id BINARY(16) NOT NULL,
    command_sha256 BINARY(32) NOT NULL,
    command_bytes MEDIUMBLOB NOT NULL,
    receipt_sha256 BINARY(32) NOT NULL,
    receipt_bytes MEDIUMBLOB NOT NULL,
    recorded_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_temporary_handover_confirmation_id(id),
    UNIQUE KEY uq_temporary_handover_confirmation_command(command_id),
    FOREIGN KEY(case_id) REFERENCES handover_case(id),
    FOREIGN KEY(authorization_id) REFERENCES temporary_authorization(id),
    FOREIGN KEY(actor_id) REFERENCES app_user(id),
    FOREIGN KEY(device_id) REFERENCES device(id)
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER temporary_handover_confirmation_no_update BEFORE UPDATE ON temporary_handover_confirmation FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Temporary handover confirmation is immutable'; END$$
CREATE TRIGGER temporary_handover_confirmation_no_delete BEFORE DELETE ON temporary_handover_confirmation FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Temporary handover confirmation is retained'; END$$
DELIMITER ;
