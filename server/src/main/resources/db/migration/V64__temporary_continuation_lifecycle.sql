SET SESSION lock_wait_timeout=10;

-- A terminal state is an append-only claim backed by a separate closure
-- proof.  The continuation row keeps the original source facts immutable;
-- this table records how the activated ticket was consumed.
CREATE TABLE temporary_source_continuation_closure (
    ticket_id BINARY(16) PRIMARY KEY,
    scope_id BINARY(16) NOT NULL,
    from_work_round BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    new_authorization_id BINARY(16) NOT NULL,
    target_submission_id BINARY(16) NULL,
    closure_receipt_id BINARY(16) NOT NULL UNIQUE,
    receipt_text MEDIUMTEXT NOT NULL,
    closed_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_temp_continuation_closure_context(scope_id,ticket_id,from_work_round),
    FOREIGN KEY(ticket_id) REFERENCES temporary_source_continuation(id),
    FOREIGN KEY(scope_id,ticket_id,from_work_round)
        REFERENCES temporary_source_continuation(scope_id,id,from_work_round),
    FOREIGN KEY(new_authorization_id) REFERENCES temporary_authorization(id),
    FOREIGN KEY(target_submission_id) REFERENCES submission(id),
    CHECK(from_work_round>0),
    CHECK(status IN ('COMPLETED','CANCELLED')),
    CHECK((status='COMPLETED' AND target_submission_id IS NOT NULL)
        OR (status='CANCELLED' AND target_submission_id IS NULL)),
    CHECK(JSON_VALID(receipt_text))
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER temp_source_continuation_closure_immutable_update
BEFORE UPDATE ON temporary_source_continuation_closure FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation closure proofs are immutable';
END$$
CREATE TRIGGER temp_source_continuation_closure_immutable_delete
BEFORE DELETE ON temporary_source_continuation_closure FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation closure proofs are retained';
END$$
DELIMITER ;

DROP TRIGGER temp_source_continuation_state_update;
DELIMITER $$
CREATE TRIGGER temp_source_continuation_state_update
BEFORE UPDATE ON temporary_source_continuation FOR EACH ROW
BEGIN
    DECLARE activation_count INT DEFAULT 0;
    DECLARE closure_count INT DEFAULT 0;
    IF NEW.id<>OLD.id OR NEW.warehouse_id<>OLD.warehouse_id OR NEW.scope_id<>OLD.scope_id
       OR NEW.previous_submission_id<>OLD.previous_submission_id OR NEW.previous_authorization_id<>OLD.previous_authorization_id
       OR NEW.previous_operator_id<>OLD.previous_operator_id OR NEW.freeze_device_id<>OLD.freeze_device_id
       OR NEW.source_recovery_epoch<>OLD.source_recovery_epoch OR NEW.from_work_round<>OLD.from_work_round
       OR NEW.to_work_round<>OLD.to_work_round OR NEW.evidence_set_id<>OLD.evidence_set_id
       OR NEW.evidence_verification_id<>OLD.evidence_verification_id
       OR NEW.retained_manifest_sha256<>OLD.retained_manifest_sha256 OR NEW.retained_manifest_version<>OLD.retained_manifest_version
       OR NEW.next_order_scan_seq<>OLD.next_order_scan_seq OR NEW.command_receipt_id<>OLD.command_receipt_id
       OR NEW.command_sha256<>OLD.command_sha256 OR NEW.command_text<>OLD.command_text OR NEW.created_at<>OLD.created_at
       OR (OLD.new_authorization_id IS NOT NULL AND (NEW.new_authorization_id IS NULL OR NEW.new_authorization_id<>OLD.new_authorization_id))
    THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation source facts are immutable';
    END IF;
    IF OLD.status='TICKET_ISSUED' AND NEW.status='ACTIVATED' THEN
        SELECT COUNT(*) INTO activation_count
        FROM temporary_source_continuation_activation a
        WHERE a.ticket_id=NEW.id AND a.scope_id=NEW.scope_id AND a.from_work_round=NEW.from_work_round
          AND a.new_authorization_id=NEW.new_authorization_id
          AND a.receipt_text=NEW.receipt_text;
        IF OLD.new_authorization_id IS NOT NULL OR NEW.new_authorization_id IS NULL
           OR NEW.receipt_text IS NULL OR activation_count<>1
        THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation activation proof is incomplete'; END IF;
    ELSEIF OLD.status='ACTIVATED' AND NEW.status IN ('COMPLETED','CANCELLED') THEN
        SELECT COUNT(*) INTO closure_count
        FROM temporary_source_continuation_closure c
        WHERE c.ticket_id=NEW.id AND c.scope_id=NEW.scope_id AND c.from_work_round=NEW.from_work_round
          AND c.status=NEW.status AND c.new_authorization_id=NEW.new_authorization_id
          AND c.receipt_text=NEW.receipt_text;
        IF NEW.new_authorization_id IS NULL OR NEW.receipt_text IS NULL OR closure_count<>1
        THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation closure proof is incomplete'; END IF;
    ELSE SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation status transition is not allowed';
    END IF;
END$$
DELIMITER ;
