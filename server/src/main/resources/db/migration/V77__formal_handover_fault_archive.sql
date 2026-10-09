SET SESSION lock_wait_timeout=10;

-- Formal archives retain their original recall/package identity without populating TEMP evidence fields.
ALTER TABLE recovery_package ADD UNIQUE KEY uq_recovery_fault_reference(id,request_id,payload_sha256);
CREATE TABLE handover_formal_fault_fence (
    case_id BINARY(16) PRIMARY KEY,
    warehouse_id BINARY(16) NOT NULL,
    scope_id BINARY(16) NOT NULL,
    submission_id BINARY(16) NOT NULL,
    assignment_id BINARY(16) NOT NULL,
    source_device_id BINARY(16) NOT NULL,
    device_version BIGINT NOT NULL,
    change_request_id BINARY(16) NOT NULL,
    recovery_package_id BINARY(16) NOT NULL,
    payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    capture_high_watermark BIGINT NOT NULL,
    audit_through_seq BIGINT NOT NULL,
    input_high_watermark BIGINT NOT NULL,
    controlled_at DATETIME(3) NOT NULL,
    command_key BINARY(16) NOT NULL UNIQUE,
    command_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    recorded_by BINARY(16) NOT NULL,
    recorded_at DATETIME(3) NOT NULL,
    KEY ix_handover_formal_fault_device(source_device_id),
    FOREIGN KEY(case_id,warehouse_id) REFERENCES handover_case(id,warehouse_id),
    FOREIGN KEY(warehouse_id,scope_id) REFERENCES submission_scope(warehouse_id,id),
    FOREIGN KEY(submission_id) REFERENCES submission(id),
    FOREIGN KEY(assignment_id) REFERENCES order_assignment(id),
    FOREIGN KEY(source_device_id) REFERENCES device(id),
    FOREIGN KEY(recovery_package_id,change_request_id,payload_sha256) REFERENCES recovery_package(id,request_id,payload_sha256),
    FOREIGN KEY(recorded_by) REFERENCES app_user(id),
    CHECK(device_version>0 AND capture_high_watermark>=0 AND audit_through_seq>0 AND input_high_watermark>=0),
    CHECK(controlled_at<=recorded_at),
    CHECK(payload_sha256 REGEXP '^[a-f0-9]{64}$' AND command_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER handover_formal_fault_binding BEFORE INSERT ON handover_formal_fault_fence FOR EACH ROW
BEGIN
    IF NOT EXISTS(SELECT 1 FROM handover_case h JOIN submission s ON s.id=h.submission_id
        JOIN order_assignment a ON a.id=s.assignment_id JOIN order_change_request r ON r.assignment_id=a.id
        JOIN recovery_package p ON p.request_id=r.id JOIN device d ON d.id=a.device_id
        WHERE h.id=NEW.case_id AND h.source_kind='FORMAL' AND h.stop_state='REQUESTED' AND h.status='EVIDENCE_PENDING'
          AND h.warehouse_id=NEW.warehouse_id AND h.scope_id=NEW.scope_id AND s.id=NEW.submission_id
          AND s.scope_id=NEW.scope_id AND s.status='ABORTED' AND a.id=NEW.assignment_id AND a.status='REVOKED'
          AND a.warehouse_id=NEW.warehouse_id AND a.order_id=h.canonical_order_id AND a.device_id=NEW.source_device_id
          AND s.freeze_device_id=NEW.source_device_id AND s.operator_id=a.operator_id
          AND d.status='DISABLED' AND d.row_version>=NEW.device_version
          AND r.id=NEW.change_request_id AND r.status='REQUESTED' AND r.stop_receipt IS NULL AND r.resolution_receipt IS NULL
          AND p.id=NEW.recovery_package_id AND BINARY p.payload_sha256=BINARY NEW.payload_sha256
          AND p.capture_high_watermark=NEW.capture_high_watermark AND p.audit_through_seq=NEW.audit_through_seq
          AND p.received_at<=NEW.controlled_at AND h.requested_by<>NEW.recorded_by AND s.operator_id<>NEW.recorded_by) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Formal fault fence requires original archived source and revoked qualification';
    END IF;
END$$
CREATE TRIGGER handover_formal_fault_no_update BEFORE UPDATE ON handover_formal_fault_fence FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Formal fault fence is immutable'; END$$
CREATE TRIGGER handover_formal_fault_no_delete BEFORE DELETE ON handover_formal_fault_fence FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Formal fault fence is retained'; END$$
CREATE TRIGGER recovery_package_formal_fault_no_update BEFORE UPDATE ON recovery_package FOR EACH ROW
BEGIN
    IF EXISTS(SELECT 1 FROM handover_formal_fault_fence WHERE recovery_package_id=OLD.id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Formal fault original package is immutable';
    END IF;
END$$
CREATE TRIGGER order_change_formal_fault_no_ack BEFORE UPDATE ON order_change_request FOR EACH ROW
BEGIN
    IF EXISTS(SELECT 1 FROM handover_formal_fault_fence WHERE change_request_id=OLD.id)
       AND (NEW.status<>OLD.status OR NOT(NEW.stop_receipt<=>OLD.stop_receipt) OR NOT(NEW.resolution_receipt<=>OLD.resolution_receipt)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Formal fault fence cannot become an original device acknowledgement';
    END IF;
END$$

DROP TRIGGER device_fault_no_reenable$$
CREATE TRIGGER device_fault_no_reenable BEFORE UPDATE ON device FOR EACH ROW
BEGIN
    IF (EXISTS(SELECT 1 FROM handover_fault_fence WHERE source_device_id=OLD.id)
        OR EXISTS(SELECT 1 FROM handover_formal_fault_fence WHERE source_device_id=OLD.id))
       AND (NEW.status<>'DISABLED' OR NOT(NEW.public_key<=>OLD.public_key) OR NOT(NEW.key_sha256<=>OLD.key_sha256)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fault device requires dedicated recovery; ordinary re-enable is forbidden';
    END IF;
END$$
DROP TRIGGER handover_fault_case_binding$$
CREATE TRIGGER handover_fault_case_binding BEFORE UPDATE ON handover_case FOR EACH ROW
BEGIN
    IF NEW.stop_state='FAULT_FENCED' AND NOT EXISTS(
        SELECT 1 FROM handover_fault_fence f JOIN device d ON d.id=f.source_device_id
        WHERE NEW.source_kind='TEMP' AND f.case_id=NEW.id AND f.warehouse_id=NEW.warehouse_id AND f.scope_id=NEW.scope_id
          AND f.submission_id=NEW.submission_id AND f.input_high_watermark=NEW.input_high_watermark
          AND d.status='DISABLED' AND d.row_version>=f.device_version)
        AND NOT EXISTS(SELECT 1 FROM handover_formal_fault_fence f JOIN device d ON d.id=f.source_device_id
        WHERE NEW.source_kind='FORMAL' AND f.case_id=NEW.id AND f.warehouse_id=NEW.warehouse_id AND f.scope_id=NEW.scope_id
          AND f.submission_id=NEW.submission_id AND f.input_high_watermark=NEW.input_high_watermark
          AND d.status='DISABLED' AND d.row_version>=f.device_version) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fault state requires durable device fence and original evidence';
    END IF;
    IF OLD.stop_state='FAULT_FENCED' AND NEW.stop_state<>'FAULT_FENCED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fault fence cannot become a device acknowledgement';
    END IF;
END$$
DELIMITER ;

-- Existing TEMP rows and archive bytes are retained. No destructive downgrade or release is provided.
