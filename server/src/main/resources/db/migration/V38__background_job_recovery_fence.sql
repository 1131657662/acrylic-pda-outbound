SET SESSION lock_wait_timeout=10;

-- Unknown historical jobs retain their original data and remain quarantined.
-- Never infer their origin from the currently restored server_state row.
ALTER TABLE background_job
    ADD COLUMN recovery_epoch BIGINT NULL,
    ADD CONSTRAINT ck_background_job_recovery_epoch CHECK(recovery_epoch IS NULL OR recovery_epoch>0),
    ADD KEY ix_background_job_epoch_queue(recovery_epoch,status,available_at,id),
    ADD KEY ix_background_job_epoch_lease(recovery_epoch,status,lease_until,id);

DELIMITER $$
CREATE TRIGGER background_job_recovery_epoch_immutable
BEFORE UPDATE ON background_job FOR EACH ROW
BEGIN
    IF NOT (OLD.recovery_epoch <=> NEW.recovery_epoch) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Background job recovery epoch is immutable';
    END IF;
END$$
DELIMITER ;
