SET SESSION lock_wait_timeout=10;

-- Additive receiving-round journal. Original submissions, tickets and owners remain unchanged.
CREATE TABLE temporary_handover_submission (
    ticket_id BINARY(16) PRIMARY KEY,
    scope_id BINARY(16) NOT NULL,
    from_work_round BIGINT NOT NULL,
    to_work_round BIGINT NOT NULL,
    submission_id BINARY(16) NOT NULL UNIQUE,
    predecessor_payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    registered_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_temp_handover_submission_round(scope_id,to_work_round),
    FOREIGN KEY(ticket_id) REFERENCES temporary_handover_activation(ticket_id),
    FOREIGN KEY(scope_id,submission_id,to_work_round) REFERENCES submission(scope_id,id,work_round),
    CHECK(from_work_round>0 AND to_work_round=from_work_round+1),
    CHECK(predecessor_payload_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB;

CREATE TABLE temporary_handover_completion (
    ticket_id BINARY(16) PRIMARY KEY,
    submission_id BINARY(16) NOT NULL UNIQUE,
    closure_receipt_id BINARY(16) NOT NULL UNIQUE,
    receipt_text MEDIUMTEXT NOT NULL,
    completed_at DATETIME(3) NOT NULL,
    FOREIGN KEY(ticket_id) REFERENCES temporary_handover_submission(ticket_id),
    FOREIGN KEY(submission_id) REFERENCES temporary_handover_submission(submission_id),
    CHECK(JSON_VALID(receipt_text))
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER temp_handover_submission_binding BEFORE INSERT ON temporary_handover_submission FOR EACH ROW
BEGIN
    DECLARE valid_context INT DEFAULT 0;
    SELECT COUNT(*) INTO valid_context FROM temporary_handover_activation_ticket t
    JOIN temporary_handover_activation a ON a.ticket_id=t.id
    JOIN handover_case h ON h.id=t.case_id AND h.status='ACTIVATED' AND h.source_kind='TEMP'
    JOIN submission_scope c ON c.id=t.scope_id AND c.canonical_order_id=t.canonical_order_id
      AND c.source_order_id IS NULL AND c.work_round=t.to_work_round AND c.current_submission_id IS NULL
    JOIN order_submission_slot l ON l.scope_id=c.id AND l.order_id=c.canonical_order_id
      AND l.work_round=c.work_round AND l.current_submission_id IS NULL AND l.phase='CONTINUING'
    JOIN submission p ON p.id=t.submission_id AND p.scope_id=c.id AND p.work_round=t.from_work_round
      AND p.temporary_authorization_id=t.source_authorization_id AND p.status='ABORTED'
    JOIN submission s ON s.id=NEW.submission_id AND s.scope_id=c.id AND s.work_round=t.to_work_round
      AND s.freeze_device_id=a.target_device_id AND s.operator_id=a.target_user_id
      AND s.temporary_authorization_id=a.new_authorization_id AND s.source_recovery_epoch=t.source_recovery_epoch
      AND s.assignment_id IS NULL AND s.continuation_id IS NULL AND s.status='REGISTERED'
    JOIN temporary_work_origin o ON o.creator_device_id=a.target_device_id AND o.client_order_id=a.new_client_order_id
      AND o.authorization_id=a.new_authorization_id
    WHERE t.id=NEW.ticket_id AND t.scope_id=NEW.scope_id AND t.from_work_round=NEW.from_work_round
      AND t.to_work_round=NEW.to_work_round AND t.payload_sha256=NEW.predecessor_payload_sha256;
    IF valid_context<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP handover submission binding is invalid'; END IF;
END$$
CREATE TRIGGER temp_handover_submission_no_update BEFORE UPDATE ON temporary_handover_submission FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP handover submission bindings are immutable'; END$$
CREATE TRIGGER temp_handover_submission_no_delete BEFORE DELETE ON temporary_handover_submission FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP handover submission bindings are retained'; END$$
CREATE TRIGGER temp_handover_submission_identity BEFORE UPDATE ON submission FOR EACH ROW
BEGIN
    IF EXISTS(SELECT 1 FROM temporary_handover_submission r WHERE r.submission_id=OLD.id)
      AND (NEW.id<>OLD.id OR NEW.scope_id<>OLD.scope_id OR NEW.freeze_device_id<>OLD.freeze_device_id
        OR NEW.operator_id<>OLD.operator_id OR NEW.work_round<>OLD.work_round OR NEW.source_recovery_epoch<>OLD.source_recovery_epoch
        OR NOT(NEW.temporary_authorization_id<=>OLD.temporary_authorization_id)
        OR NOT(NEW.assignment_id<=>OLD.assignment_id) OR NOT(NEW.continuation_id<=>OLD.continuation_id))
    THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP handover submission identity is immutable'; END IF;
END$$
CREATE TRIGGER temp_handover_completion_binding BEFORE INSERT ON temporary_handover_completion FOR EACH ROW
BEGIN
    DECLARE valid_context INT DEFAULT 0;
    SELECT COUNT(*) INTO valid_context FROM temporary_handover_submission r JOIN submission s ON s.id=r.submission_id
    WHERE r.ticket_id=NEW.ticket_id AND r.submission_id=NEW.submission_id AND s.status='SUCCEEDED'
      AND s.receipt_text=NEW.receipt_text;
    IF valid_context<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP handover completion binding is invalid'; END IF;
END$$
CREATE TRIGGER temp_handover_completion_no_update BEFORE UPDATE ON temporary_handover_completion FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP handover completion proofs are immutable'; END$$
CREATE TRIGGER temp_handover_completion_no_delete BEFORE DELETE ON temporary_handover_completion FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP handover completion proofs are retained'; END$$
DELIMITER ;
