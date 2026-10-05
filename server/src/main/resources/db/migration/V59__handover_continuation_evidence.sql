SET SESSION lock_wait_timeout=10;

CREATE TABLE handover_continuation (
    id BINARY(16) PRIMARY KEY,
    case_id BINARY(16) NOT NULL UNIQUE,
    command_id BINARY(16) NOT NULL UNIQUE,
    command_sha256 BINARY(32) NOT NULL,
    command_text MEDIUMTEXT NOT NULL,
    order_continuation_id BINARY(16) NOT NULL UNIQUE,
    assignment_id BINARY(16) NOT NULL,
    authorization_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    device_id BINARY(16) NOT NULL,
    recovery_package_sha256 BINARY(32) NOT NULL,
    retained_manifest_sha256 BINARY(32) NOT NULL,
    next_order_scan_seq BIGINT NOT NULL,
    receipt_sha256 BINARY(32) NOT NULL,
    receipt_text MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_handover_cont_case FOREIGN KEY (case_id) REFERENCES handover_case(id),
    CONSTRAINT fk_handover_cont_order FOREIGN KEY (order_continuation_id) REFERENCES order_continuation(id),
    CONSTRAINT fk_handover_cont_assignment FOREIGN KEY (assignment_id) REFERENCES order_assignment(id),
    CONSTRAINT fk_handover_cont_authorization FOREIGN KEY (authorization_id) REFERENCES offline_authorization(id),
    CONSTRAINT fk_handover_cont_actor FOREIGN KEY (actor_id) REFERENCES app_user(id),
    CONSTRAINT fk_handover_cont_device FOREIGN KEY (device_id) REFERENCES device(id),
    CHECK (next_order_scan_seq > 0),
    CHECK (JSON_VALID(command_text)),
    CHECK (JSON_VALID(receipt_text))
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER handover_continuation_no_update BEFORE UPDATE ON handover_continuation FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Handover continuation evidence is immutable'; END$$
CREATE TRIGGER handover_continuation_no_delete BEFORE DELETE ON handover_continuation FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Handover continuation evidence is retained'; END$$
DELIMITER ;
