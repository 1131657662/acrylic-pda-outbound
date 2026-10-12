-- A physical return does not itself release stock. Its separate maker/checker decision remains append-only.
SET SESSION lock_wait_timeout=10;
ALTER TABLE correction_command_receipt DROP CHECK ck_correction_receipt_command_type,
    MODIFY command_type VARCHAR(32) NOT NULL,
    ADD CONSTRAINT ck_correction_receipt_command_type CHECK(command_type IN
      ('CREATE','APPROVE','WITHDRAW','WITHDRAW_CONFIRM','RECONCILE_OPEN','RECONCILE_ADVANCE',
       'RECONCILE_DISCOVER','RECONCILE_SUBMIT','RECONCILE_APPROVE',
       'RECONCILE_RETURN_DISCOVER','RECONCILE_RETURN_SUBMIT','RECONCILE_RETURN_APPROVE'));
CREATE TABLE returned_stock_release (
    case_id BINARY(16) PRIMARY KEY,
    original_dispatch_id BINARY(16) NOT NULL UNIQUE,
    cycle_reopening_id BINARY(16) NOT NULL UNIQUE,
    approval_receipt_id BINARY(16) NOT NULL UNIQUE,
    FOREIGN KEY(case_id) REFERENCES reconciliation_evidence(case_id),
    FOREIGN KEY(original_dispatch_id) REFERENCES dispatch_record(id),
    FOREIGN KEY(cycle_reopening_id) REFERENCES cycle_reopening(id),
    FOREIGN KEY(approval_receipt_id) REFERENCES correction_command_receipt(id)
) ENGINE=InnoDB;
DELIMITER $$
CREATE TRIGGER returned_stock_release_no_update BEFORE UPDATE ON returned_stock_release FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Returned stock release is immutable'; END$$
CREATE TRIGGER returned_stock_release_no_delete BEFORE DELETE ON returned_stock_release FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Returned stock release is retained'; END$$
DELIMITER ;
