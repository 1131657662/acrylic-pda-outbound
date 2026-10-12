-- Quarantine clearance retains its original disposition and all signed evidence.
SET SESSION lock_wait_timeout=10;
ALTER TABLE correction_command_receipt DROP CHECK ck_correction_receipt_command_type,
    ADD CONSTRAINT ck_correction_receipt_command_type CHECK(command_type IN
      ('CREATE','APPROVE','WITHDRAW','WITHDRAW_CONFIRM','RECONCILE_OPEN','RECONCILE_ADVANCE',
       'RECONCILE_DISCOVER','RECONCILE_SUBMIT','RECONCILE_APPROVE',
       'RECONCILE_RETURN_DISCOVER','RECONCILE_RETURN_SUBMIT','RECONCILE_RETURN_APPROVE',
       'RECONCILE_QUARANTINE_DISCOVER','RECONCILE_QUARANTINE_SUBMIT','RECONCILE_QUARANTINE_APPROVE'));
ALTER TABLE reconciliation_request MODIFY order_id BINARY(16) NULL;
CREATE TABLE quarantine_origin (
    case_id BINARY(16) PRIMARY KEY,
    disposition_id BINARY(16) NOT NULL,
    bale_id BINARY(16) NOT NULL,
    original_receipt_id BINARY(16) NOT NULL,
    UNIQUE KEY uq_quarantine_case_origin(disposition_id,bale_id),
    FOREIGN KEY(case_id) REFERENCES reconciliation_request(case_id),
    FOREIGN KEY(disposition_id,bale_id) REFERENCES temporary_physical_disposition_item(disposition_id,bale_id),
    FOREIGN KEY(original_receipt_id) REFERENCES temporary_command_receipt(id)
) ENGINE=InnoDB;
CREATE TABLE quarantine_release (
    case_id BINARY(16) PRIMARY KEY,
    approval_receipt_id BINARY(16) NOT NULL UNIQUE,
    FOREIGN KEY(case_id) REFERENCES quarantine_origin(case_id),
    FOREIGN KEY(case_id) REFERENCES reconciliation_evidence(case_id),
    FOREIGN KEY(approval_receipt_id) REFERENCES correction_command_receipt(id)
) ENGINE=InnoDB;
DELIMITER $$
CREATE TRIGGER quarantine_origin_no_update BEFORE UPDATE ON quarantine_origin FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Quarantine origin is immutable'; END$$
CREATE TRIGGER quarantine_origin_no_delete BEFORE DELETE ON quarantine_origin FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Quarantine origin is retained'; END$$
CREATE TRIGGER quarantine_release_no_update BEFORE UPDATE ON quarantine_release FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Quarantine release is immutable'; END$$
CREATE TRIGGER quarantine_release_no_delete BEFORE DELETE ON quarantine_release FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Quarantine release is retained'; END$$
DELIMITER ;
