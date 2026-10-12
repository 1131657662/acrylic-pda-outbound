-- Append-only references for business reconciliation. Historical cases and receipt bytes stay untouched.
SET SESSION lock_wait_timeout=10;

ALTER TABLE correction_command_receipt DROP CHECK ck_correction_receipt_command_type,
    ADD CONSTRAINT ck_correction_receipt_command_type CHECK(command_type IN
      ('CREATE','APPROVE','WITHDRAW','WITHDRAW_CONFIRM','RECONCILE_OPEN','RECONCILE_ADVANCE',
       'RECONCILE_DISCOVER','RECONCILE_SUBMIT','RECONCILE_APPROVE'));

CREATE TABLE reconciliation_request (
    case_id BINARY(16) PRIMARY KEY,
    order_id BINARY(16) NOT NULL,
    opened_receipt_id BINARY(16) NOT NULL UNIQUE,
    FOREIGN KEY(case_id) REFERENCES inventory_reconciliation(id),
    FOREIGN KEY(order_id) REFERENCES outbound_order(id),
    FOREIGN KEY(opened_receipt_id) REFERENCES correction_command_receipt(id)
) ENGINE=InnoDB;

CREATE TABLE reconciliation_evidence (
    case_id BINARY(16) PRIMARY KEY,
    submitted_receipt_id BINARY(16) NOT NULL UNIQUE,
    FOREIGN KEY(case_id) REFERENCES reconciliation_request(case_id),
    FOREIGN KEY(submitted_receipt_id) REFERENCES correction_command_receipt(id)
) ENGINE=InnoDB;

CREATE TABLE reconciliation_attachment_use (
    case_id BINARY(16) NOT NULL,
    attachment_id BINARY(16) NOT NULL,
    PRIMARY KEY(case_id,attachment_id),
    FOREIGN KEY(case_id) REFERENCES reconciliation_evidence(case_id),
    FOREIGN KEY(attachment_id) REFERENCES correction_attachment(id)
) ENGINE=InnoDB;

CREATE TABLE reconciliation_posting (
    case_id BINARY(16) PRIMARY KEY,
    ledger_id BINARY(16) NOT NULL UNIQUE,
    approval_receipt_id BINARY(16) NOT NULL UNIQUE,
    FOREIGN KEY(case_id) REFERENCES reconciliation_evidence(case_id),
    FOREIGN KEY(ledger_id) REFERENCES correction_ledger(id),
    FOREIGN KEY(approval_receipt_id) REFERENCES correction_command_receipt(id)
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER reconciliation_request_no_update BEFORE UPDATE ON reconciliation_request FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Reconciliation request is immutable'; END$$
CREATE TRIGGER reconciliation_request_no_delete BEFORE DELETE ON reconciliation_request FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Reconciliation request is retained'; END$$
CREATE TRIGGER reconciliation_evidence_no_update BEFORE UPDATE ON reconciliation_evidence FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Reconciliation evidence is immutable'; END$$
CREATE TRIGGER reconciliation_evidence_no_delete BEFORE DELETE ON reconciliation_evidence FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Reconciliation evidence is retained'; END$$
CREATE TRIGGER reconciliation_use_no_update BEFORE UPDATE ON reconciliation_attachment_use FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Reconciliation attachment reference is immutable'; END$$
CREATE TRIGGER reconciliation_use_no_delete BEFORE DELETE ON reconciliation_attachment_use FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Reconciliation attachment reference is retained'; END$$
CREATE TRIGGER reconciliation_posting_no_update BEFORE UPDATE ON reconciliation_posting FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Reconciliation posting is immutable'; END$$
CREATE TRIGGER reconciliation_posting_no_delete BEFORE DELETE ON reconciliation_posting FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Reconciliation posting is retained'; END$$
DELIMITER ;
