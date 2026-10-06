SET SESSION lock_wait_timeout=10;

ALTER TABLE temporary_physical_disposition_history
    ADD UNIQUE KEY uq_temp_disposition_continuation(disposition_id,scope_id,work_round,bale_id,dispatch_cycle);

-- A TEMP continuation is a separate source record. It never reopens the old
-- submission and it does not reuse a formal assignment or authorization.
CREATE TABLE temporary_source_continuation (
    id BINARY(16) PRIMARY KEY,
    warehouse_id BINARY(16) NOT NULL,
    scope_id BINARY(16) NOT NULL,
    previous_submission_id BINARY(16) NOT NULL,
    previous_authorization_id BINARY(16) NOT NULL,
    previous_operator_id BINARY(16) NOT NULL,
    freeze_device_id BINARY(16) NOT NULL,
    source_recovery_epoch BIGINT NOT NULL,
    from_work_round BIGINT NOT NULL,
    to_work_round BIGINT NOT NULL,
    evidence_set_id BINARY(16) NOT NULL,
    evidence_verification_id BINARY(16) NOT NULL,
    retained_manifest_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    retained_manifest_version BIGINT NOT NULL,
    next_order_scan_seq BIGINT NOT NULL,
    command_receipt_id BINARY(16) NOT NULL,
    command_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) NOT NULL,
    new_authorization_id BINARY(16) NULL,
    command_text MEDIUMTEXT NOT NULL,
    receipt_text MEDIUMTEXT NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_temp_continuation_round(scope_id,to_work_round),
    UNIQUE KEY uq_temp_continuation_submission(previous_submission_id),
    UNIQUE KEY uq_temp_continuation_receipt(command_receipt_id),
    UNIQUE KEY uq_temp_continuation_new_authorization(new_authorization_id),
    UNIQUE KEY uq_temp_continuation_context(id,scope_id,from_work_round),
    FOREIGN KEY(warehouse_id,scope_id) REFERENCES submission_scope(warehouse_id,id),
    FOREIGN KEY(scope_id,previous_submission_id,from_work_round) REFERENCES submission(scope_id,id,work_round),
    FOREIGN KEY(previous_authorization_id) REFERENCES temporary_authorization(id),
    FOREIGN KEY(previous_operator_id) REFERENCES app_user(id),
    FOREIGN KEY(freeze_device_id) REFERENCES device(id),
    FOREIGN KEY(evidence_set_id,scope_id,previous_submission_id,from_work_round)
        REFERENCES source_evidence_set(id,scope_id,submission_id,work_round),
    FOREIGN KEY(evidence_verification_id,evidence_set_id,scope_id)
        REFERENCES source_evidence_verification(id,set_id,scope_id),
    CHECK(source_recovery_epoch>0), CHECK(from_work_round>0 AND to_work_round=from_work_round+1),
    CHECK(retained_manifest_version>0), CHECK(next_order_scan_seq>0),
    CHECK(status IN ('TICKET_ISSUED','ACTIVATED','COMPLETED','CANCELLED')),
    CHECK((status='TICKET_ISSUED' AND new_authorization_id IS NULL) OR status<>'TICKET_ISSUED'),
    CHECK(JSON_VALID(command_text)), CHECK(receipt_text IS NULL OR JSON_VALID(receipt_text))
) ENGINE=InnoDB;

CREATE TABLE temporary_source_continuation_bale (
    continuation_id BINARY(16) NOT NULL,
    scope_id BINARY(16) NOT NULL,
    from_work_round BIGINT NOT NULL,
    disposition_id BINARY(16) NOT NULL,
    bale_id BINARY(16) NOT NULL,
    expected_cycle BIGINT NOT NULL,
    record_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY(continuation_id,bale_id),
    KEY ix_temp_continuation_bale_context(continuation_id,scope_id,from_work_round),
    UNIQUE KEY uq_temp_continuation_history(continuation_id,disposition_id,bale_id),
    FOREIGN KEY(continuation_id) REFERENCES temporary_source_continuation(id),
    CONSTRAINT fk_temp_continuation_bale_context FOREIGN KEY(continuation_id,scope_id,from_work_round)
        REFERENCES temporary_source_continuation(id,scope_id,from_work_round),
    FOREIGN KEY(scope_id,from_work_round,bale_id)
        REFERENCES temporary_physical_disposition_history(scope_id,work_round,bale_id),
    CONSTRAINT fk_temp_continuation_bale_history FOREIGN KEY(disposition_id,scope_id,from_work_round,bale_id,expected_cycle)
        REFERENCES temporary_physical_disposition_history(disposition_id,scope_id,work_round,bale_id,dispatch_cycle),
    FOREIGN KEY(bale_id) REFERENCES bale(id),
    CHECK(from_work_round>0 AND expected_cycle>0),
    CHECK(record_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER temp_source_continuation_immutable_update
BEFORE UPDATE ON temporary_source_continuation FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation tickets are immutable';
END$$
CREATE TRIGGER temp_source_continuation_immutable_delete
BEFORE DELETE ON temporary_source_continuation FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation tickets are retained';
END$$
CREATE TRIGGER temp_source_continuation_bale_immutable_update
BEFORE UPDATE ON temporary_source_continuation_bale FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation retention rows are immutable';
END$$
CREATE TRIGGER temp_source_continuation_bale_immutable_delete
BEFORE DELETE ON temporary_source_continuation_bale FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation retention rows are retained';
END$$
DELIMITER ;
