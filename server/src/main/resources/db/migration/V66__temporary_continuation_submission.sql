SET SESSION lock_wait_timeout=10;

-- Additive registration journal. No historical source, authorization, slot or K is rewritten.
CREATE TABLE temporary_source_continuation_submission (
    ticket_id BINARY(16) PRIMARY KEY,
    scope_id BINARY(16) NOT NULL,
    from_work_round BIGINT NOT NULL,
    to_work_round BIGINT NOT NULL,
    submission_id BINARY(16) NOT NULL UNIQUE,
    predecessor_payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    registered_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_temp_continuation_submission_round(scope_id,to_work_round),
    FOREIGN KEY(scope_id,ticket_id,from_work_round)
        REFERENCES temporary_source_continuation_activation(scope_id,ticket_id,from_work_round),
    FOREIGN KEY(scope_id,submission_id,to_work_round) REFERENCES submission(scope_id,id,work_round),
    CHECK(from_work_round>0 AND to_work_round=from_work_round+1),
    CHECK(predecessor_payload_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER temp_continuation_submission_insert_guard
BEFORE INSERT ON temporary_source_continuation_submission FOR EACH ROW
BEGIN
    DECLARE valid_context INT DEFAULT 0;
    SELECT COUNT(*) INTO valid_context
    FROM temporary_source_continuation t
    JOIN temporary_source_continuation_activation a ON a.ticket_id=t.id AND a.scope_id=t.scope_id
      AND a.from_work_round=t.from_work_round AND a.new_authorization_id=t.new_authorization_id
      AND a.target_device_id=t.freeze_device_id AND a.target_operator_id=t.previous_operator_id
    JOIN submission_scope c ON c.id=t.scope_id AND c.source_order_id IS NULL
      AND c.current_submission_id=t.previous_submission_id AND c.work_round=t.from_work_round
    JOIN submission p ON p.id=t.previous_submission_id AND p.scope_id=t.scope_id
      AND p.work_round=t.from_work_round AND p.status='ABORTED'
      AND p.temporary_authorization_id=t.previous_authorization_id AND p.operator_id=t.previous_operator_id
      AND p.freeze_device_id=t.freeze_device_id AND p.source_recovery_epoch=t.source_recovery_epoch
    JOIN submission s ON s.id=NEW.submission_id AND s.scope_id=t.scope_id AND s.work_round=t.to_work_round
      AND s.status='REGISTERED' AND s.assignment_id IS NULL AND s.continuation_id IS NULL
      AND s.temporary_authorization_id=a.new_authorization_id AND s.freeze_device_id=a.target_device_id
      AND s.operator_id=a.target_operator_id AND s.source_recovery_epoch=t.source_recovery_epoch
    JOIN temporary_work_origin o ON o.creator_device_id=a.target_device_id
      AND o.client_order_id=a.new_client_order_id AND o.authorization_id=a.new_authorization_id
    JOIN temporary_authorization n ON n.id=a.new_authorization_id AND n.device_id=a.target_device_id
      AND n.user_id=a.target_operator_id AND n.warehouse_id=t.warehouse_id AND n.recovery_epoch=t.source_recovery_epoch
    JOIN source_evidence_set e ON e.id=t.evidence_set_id AND e.scope_id=t.scope_id
      AND e.submission_id=p.id AND e.work_round=p.work_round
      AND e.order_scan_high_watermark>=0 AND e.order_scan_high_watermark<9223372036854775807
      AND e.order_scan_high_watermark<=e.capture_high_watermark
      AND t.next_order_scan_seq=e.order_scan_high_watermark+1
    JOIN source_evidence_verification v ON v.id=t.evidence_verification_id AND v.set_id=e.id
      AND v.scope_id=t.scope_id AND v.submission_id=p.id AND v.work_round=p.work_round
      AND v.set_sha256=e.set_sha256 AND v.result='VERIFIED_COMPLETE_AT_STOP'
    WHERE t.id=NEW.ticket_id AND t.scope_id=NEW.scope_id AND t.status='ACTIVATED'
      AND t.from_work_round=NEW.from_work_round AND t.to_work_round=NEW.to_work_round
      AND (EXISTS(SELECT 1 FROM submission_payload v WHERE v.submission_id=p.id
                    AND v.payload_sha256=NEW.predecessor_payload_sha256)
        OR (NOT EXISTS(SELECT 1 FROM submission_payload v WHERE v.submission_id=p.id)
          AND EXISTS(SELECT 1 FROM source_evidence_item i JOIN source_evidence_object f ON f.id=i.object_id
            AND f.scope_id=i.scope_id AND f.submission_id=i.submission_id AND f.work_round=i.work_round
            WHERE i.set_id=e.id AND i.scope_id=t.scope_id AND i.submission_id=p.id AND i.work_round=p.work_round
              AND i.item_kind='STOP' AND i.record_status='FROZEN' AND i.original_record_id=p.id
              AND f.kind='LOCAL_FROZEN_PAYLOAD' AND f.origin_device_id=t.freeze_device_id
              AND f.warehouse_id=t.warehouse_id AND f.uploaded_by=t.previous_operator_id
              AND i.record_sha256=f.bytes_sha256 AND f.bytes_sha256=NEW.predecessor_payload_sha256)));
    IF valid_context<>1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation submission binding is invalid';
    END IF;
END$$
CREATE TRIGGER temp_continuation_submission_immutable_update
BEFORE UPDATE ON temporary_source_continuation_submission FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation submission bindings are immutable';
END$$
CREATE TRIGGER temp_continuation_submission_immutable_delete
BEFORE DELETE ON temporary_source_continuation_submission FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation submission bindings are retained';
END$$
CREATE TRIGGER temp_continuation_activation_immutable_update
BEFORE UPDATE ON temporary_source_continuation_activation FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation activations are immutable';
END$$
CREATE TRIGGER temp_continuation_activation_immutable_delete
BEFORE DELETE ON temporary_source_continuation_activation FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation activations are retained';
END$$
CREATE TRIGGER temp_continuation_bound_submission_identity
BEFORE UPDATE ON submission FOR EACH ROW
BEGIN
    IF EXISTS(SELECT 1 FROM temporary_source_continuation_submission r WHERE r.submission_id=OLD.id)
      AND (NEW.id<>OLD.id OR NEW.scope_id<>OLD.scope_id OR NEW.freeze_device_id<>OLD.freeze_device_id
        OR NEW.operator_id<>OLD.operator_id OR NEW.work_round<>OLD.work_round
        OR NEW.source_recovery_epoch<>OLD.source_recovery_epoch
        OR NOT(NEW.temporary_authorization_id<=>OLD.temporary_authorization_id)
        OR NOT(NEW.assignment_id<=>OLD.assignment_id) OR NOT(NEW.continuation_id<=>OLD.continuation_id))
    THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation submission identity is immutable';
    END IF;
END$$
DELIMITER ;
