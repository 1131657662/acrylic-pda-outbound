SET SESSION lock_wait_timeout=10;
ALTER TABLE temporary_physical_disposition_item ADD COLUMN action VARCHAR(16) NULL AFTER disposition;
UPDATE temporary_physical_disposition_item SET action='QUARANTINE' WHERE action IS NULL;
ALTER TABLE temporary_physical_disposition_item MODIFY action VARCHAR(16) NOT NULL,
 ADD CONSTRAINT ck_temp_disposition_action CHECK(action IN ('RELEASE','QUARANTINE'));
