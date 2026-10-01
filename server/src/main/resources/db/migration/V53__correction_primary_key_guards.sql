-- Protect identities without replacing the existing fact/lifecycle triggers.
-- MySQL DDL commits implicitly; use forward repair if applying either trigger fails.
SET @p08_previous_lock_wait_timeout = @@SESSION.lock_wait_timeout;
SET SESSION lock_wait_timeout = 15;

DELIMITER $$

CREATE TRIGGER correction_document_id_immutable
BEFORE UPDATE ON correction_document FOR EACH ROW
PRECEDES correction_document_facts_immutable
BEGIN
    IF NOT (OLD.id <=> NEW.id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction document identity is immutable';
    END IF;
END$$

CREATE TRIGGER reconciliation_id_immutable
BEFORE UPDATE ON inventory_reconciliation FOR EACH ROW
PRECEDES reconciliation_facts_immutable
BEGIN
    IF NOT (OLD.id <=> NEW.id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Inventory reconciliation identity is immutable';
    END IF;
END$$

DELIMITER ;

SET SESSION lock_wait_timeout = @p08_previous_lock_wait_timeout;
