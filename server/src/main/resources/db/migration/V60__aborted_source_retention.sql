SET SESSION lock_wait_timeout=10;

ALTER TABLE temporary_physical_disposition_item
    DROP CHECK ck_temp_disposition_action,
    ADD CONSTRAINT ck_temp_disposition_action CHECK(action IN ('RELEASE','RETAIN','QUARANTINE'));

DELIMITER $$
CREATE TRIGGER temp_retention_requires_aborted BEFORE INSERT ON temporary_physical_disposition_item FOR EACH ROW
BEGIN
    IF NEW.action='RETAIN' AND NOT EXISTS (
        SELECT 1 FROM temporary_physical_disposition d
        JOIN submission s ON s.id=d.submission_id AND s.scope_id=d.scope_id AND s.work_round=d.work_round
        WHERE d.id=NEW.disposition_id AND d.close_mode=FALSE AND s.status='ABORTED'
    ) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Only an aborted open source can retain physical bales';
    END IF;
END$$
CREATE TRIGGER temp_disposition_immutable_update BEFORE UPDATE ON temporary_physical_disposition FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Physical disposition evidence is immutable';
END$$
CREATE TRIGGER temp_disposition_immutable_delete BEFORE DELETE ON temporary_physical_disposition FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Physical disposition evidence is immutable';
END$$
CREATE TRIGGER temp_disposition_item_immutable_update BEFORE UPDATE ON temporary_physical_disposition_item FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Physical disposition items are immutable';
END$$
CREATE TRIGGER temp_disposition_item_immutable_delete BEFORE DELETE ON temporary_physical_disposition_item FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Physical disposition items are immutable';
END$$
DELIMITER ;
