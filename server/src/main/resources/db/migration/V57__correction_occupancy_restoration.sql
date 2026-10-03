-- P-R07-08: a confirmed withdrawal restores the exact original occupancy only while the reopened bale is untouched.
-- The receipt is inserted later in the same command transaction, so its id is retained as an immutable reference.
SET @occupancy_restoration_previous_lock_wait_timeout = @@SESSION.lock_wait_timeout;
SET SESSION lock_wait_timeout = 15;

CREATE TABLE correction_occupancy_restoration (
    id BINARY(16) PRIMARY KEY,
    original_dispatch_id BINARY(16) NOT NULL UNIQUE,
    correction_line_id BINARY(16) NOT NULL,
    withdrawal_document_id BINARY(16) NOT NULL,
    withdrawal_receipt_id BINARY(16) NOT NULL,
    cycle_before INT NOT NULL,
    cycle_after INT NOT NULL,
    restored_by BINARY(16) NOT NULL,
    restored_at DATETIME(3) NOT NULL,
    FOREIGN KEY(original_dispatch_id) REFERENCES dispatch_record(id),
    FOREIGN KEY(correction_line_id) REFERENCES correction_line(id),
    FOREIGN KEY(withdrawal_document_id) REFERENCES correction_document(id),
    FOREIGN KEY(withdrawal_receipt_id) REFERENCES correction_command_receipt(id),
    FOREIGN KEY(restored_by) REFERENCES app_user(id),
    CHECK(cycle_after = cycle_before + 1)
) ENGINE=InnoDB;

DELIMITER $$

CREATE TRIGGER correction_occupancy_restoration_no_update
BEFORE UPDATE ON correction_occupancy_restoration FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction occupancy restoration is append-only';
END$$

CREATE TRIGGER correction_occupancy_restoration_no_delete
BEFORE DELETE ON correction_occupancy_restoration FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction occupancy restoration is retained';
END$$

DELIMITER ;

SET SESSION lock_wait_timeout = @occupancy_restoration_previous_lock_wait_timeout;
