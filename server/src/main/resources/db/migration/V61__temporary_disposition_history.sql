SET SESSION lock_wait_timeout=10;

-- The item row remains the command's immutable record. This relation preserves
-- the source/round uniqueness that cannot be expressed across its parent row.
ALTER TABLE temporary_physical_disposition
    ADD KEY ix_temp_disposition_identity(id,scope_id,submission_id,work_round);

CREATE TABLE temporary_physical_disposition_history (
    disposition_id BINARY(16) NOT NULL,
    scope_id BINARY(16) NOT NULL,
    submission_id BINARY(16) NOT NULL,
    work_round BIGINT NOT NULL,
    bale_id BINARY(16) NOT NULL,
    dispatch_cycle BIGINT NOT NULL,
    disposition VARCHAR(32) NOT NULL,
    action VARCHAR(16) NOT NULL,
    PRIMARY KEY(disposition_id,bale_id),
    UNIQUE KEY uq_temp_disposition_source_round_bale(scope_id,work_round,bale_id),
    FOREIGN KEY(disposition_id,bale_id) REFERENCES temporary_physical_disposition_item(disposition_id,bale_id),
    FOREIGN KEY(disposition_id,scope_id,submission_id,work_round)
        REFERENCES temporary_physical_disposition(id,scope_id,submission_id,work_round),
    FOREIGN KEY(bale_id) REFERENCES bale(id),
    CHECK(work_round>0), CHECK(dispatch_cycle>0),
    CHECK(disposition='PHYSICAL_DISPOSED'),
    CHECK(action IN ('RELEASE','RETAIN','QUARANTINE'))
) ENGINE=InnoDB;

INSERT INTO temporary_physical_disposition_history
    (disposition_id,scope_id,submission_id,work_round,bale_id,dispatch_cycle,disposition,action)
SELECT i.disposition_id,d.scope_id,d.submission_id,d.work_round,i.bale_id,i.dispatch_cycle,i.disposition,i.action
FROM temporary_physical_disposition_item i
JOIN temporary_physical_disposition d ON d.id=i.disposition_id;

ALTER TABLE temporary_physical_disposition_item
    ADD KEY ix_temp_disposition_item_bale(bale_id),
    DROP INDEX uq_temp_disposition_bale;

DELIMITER $$
CREATE TRIGGER temp_disposition_history_after_insert
AFTER INSERT ON temporary_physical_disposition_item FOR EACH ROW
BEGIN
    INSERT INTO temporary_physical_disposition_history
        (disposition_id,scope_id,submission_id,work_round,bale_id,dispatch_cycle,disposition,action)
    SELECT NEW.disposition_id,d.scope_id,d.submission_id,d.work_round,NEW.bale_id,NEW.dispatch_cycle,NEW.disposition,NEW.action
    FROM temporary_physical_disposition d WHERE d.id=NEW.disposition_id;
END$$

CREATE TRIGGER temp_disposition_history_immutable_update
BEFORE UPDATE ON temporary_physical_disposition_history FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Physical disposition history is immutable';
END$$

CREATE TRIGGER temp_disposition_history_immutable_delete
BEFORE DELETE ON temporary_physical_disposition_history FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Physical disposition history is retained';
END$$
DELIMITER ;
