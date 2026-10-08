SET SESSION lock_wait_timeout=10;

-- Scoped receiving credentials may be shorter than the general authorization;
-- the upper bound is unchanged and the activation trigger checks its parent.
ALTER TABLE temporary_authorization DROP CHECK temporary_authorization_chk_6,
    ADD CONSTRAINT ck_temp_authorization_duration CHECK(TIMESTAMPDIFF(SECOND,issued_at,expires_at) BETWEEN 1 AND 172800);

-- A confirmation is only usable from the PDA session that produced it. Older
-- confirmations remain readable but cannot activate without this binding.
ALTER TABLE temporary_handover_confirmation
    ADD COLUMN session_id BINARY(16) NULL,
    ADD KEY ix_temp_handover_confirmation_session(session_id),
    ADD CONSTRAINT fk_temp_handover_confirmation_session FOREIGN KEY(session_id) REFERENCES pda_session(id);

-- PREPARE freezes the exact TEMP source facts. The ticket is append-only; a
-- separate consumption row gives one-time semantics without rewriting evidence.
CREATE TABLE temporary_handover_activation_ticket (
    id BINARY(16) PRIMARY KEY,
    case_id BINARY(16) NOT NULL,
    scope_id BINARY(16) NOT NULL,
    submission_id BINARY(16) NOT NULL,
    canonical_order_id BINARY(16) NOT NULL,
    source_authorization_id BINARY(16) NOT NULL,
    source_client_order_id BINARY(16) NOT NULL,
    source_evidence_set_id BINARY(16) NOT NULL,
    source_recovery_epoch BIGINT NOT NULL,
    from_work_round BIGINT NOT NULL,
    to_work_round BIGINT NOT NULL,
    input_high_watermark BIGINT NOT NULL,
    prepared_ticket_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    checklist_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_prefix_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_authorization_id BINARY(16) NOT NULL,
    target_device_id BINARY(16) NOT NULL,
    target_user_id BINARY(16) NOT NULL,
    target_user_auth_version BIGINT NOT NULL,
    target_device_version BIGINT NOT NULL,
    ticket_bytes MEDIUMBLOB NOT NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_temp_handover_ticket_case(case_id),
    UNIQUE KEY uq_temp_handover_ticket_round(scope_id,from_work_round),
    UNIQUE KEY uq_temp_handover_ticket_context(id,scope_id,from_work_round),
    FOREIGN KEY(case_id) REFERENCES handover_case(id),
    FOREIGN KEY(scope_id,submission_id,from_work_round) REFERENCES submission(scope_id,id,work_round),
    FOREIGN KEY(source_authorization_id) REFERENCES temporary_authorization(id),
    FOREIGN KEY(target_authorization_id) REFERENCES temporary_authorization(id),
    FOREIGN KEY(source_evidence_set_id,scope_id) REFERENCES source_evidence_set(id,scope_id),
    FOREIGN KEY(target_device_id) REFERENCES device(id),
    FOREIGN KEY(target_user_id) REFERENCES app_user(id),
    CHECK(source_recovery_epoch>0),
    CHECK(from_work_round>0 AND to_work_round=from_work_round+1),
    CHECK(input_high_watermark>=0),
    CHECK(target_user_auth_version>0 AND target_device_version>0),
    CHECK(OCTET_LENGTH(ticket_bytes) BETWEEN 1 AND 262144),
    CHECK(prepared_ticket_sha256 REGEXP '^[0-9a-f]{64}$'),
    CHECK(payload_sha256 REGEXP '^[0-9a-f]{64}$'),
    CHECK(checklist_sha256 REGEXP '^[0-9a-f]{64}$'),
    CHECK(source_prefix_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB;

CREATE TABLE temporary_handover_activation_owner (
    ticket_id BINARY(16) NOT NULL,
    bale_id BINARY(16) NOT NULL,
    dispatch_cycle BIGINT NOT NULL,
    record_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY(ticket_id,bale_id),
    FOREIGN KEY(ticket_id) REFERENCES temporary_handover_activation_ticket(id),
    FOREIGN KEY(bale_id) REFERENCES bale(id),
    CHECK(dispatch_cycle>0),
    CHECK(record_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB;

CREATE TABLE temporary_handover_activation (
    ticket_id BINARY(16) PRIMARY KEY,
    proof_id BINARY(16) NOT NULL UNIQUE,
    confirmation_id BINARY(16) NOT NULL UNIQUE,
    new_authorization_id BINARY(16) NOT NULL UNIQUE,
    new_client_order_id BINARY(16) NOT NULL UNIQUE,
    target_device_id BINARY(16) NOT NULL,
    target_user_id BINARY(16) NOT NULL,
    target_session_id BINARY(16) NOT NULL,
    from_work_round BIGINT NOT NULL,
    to_work_round BIGINT NOT NULL,
    start_order_scan_seq BIGINT NOT NULL,
    retained_count BIGINT NOT NULL,
    receipt_text MEDIUMTEXT NOT NULL,
    activated_at DATETIME(3) NOT NULL,
    FOREIGN KEY(ticket_id) REFERENCES temporary_handover_activation_ticket(id),
    FOREIGN KEY(confirmation_id) REFERENCES temporary_handover_confirmation(id),
    FOREIGN KEY(new_authorization_id) REFERENCES temporary_authorization(id),
    FOREIGN KEY(target_device_id) REFERENCES device(id),
    FOREIGN KEY(target_user_id) REFERENCES app_user(id),
    FOREIGN KEY(target_session_id) REFERENCES pda_session(id),
    FOREIGN KEY(target_device_id,new_client_order_id) REFERENCES temporary_work_origin(creator_device_id,client_order_id),
    CHECK(from_work_round>0 AND to_work_round=from_work_round+1),
    CHECK(start_order_scan_seq>0 AND retained_count>=0),
    CHECK(JSON_VALID(receipt_text))
) ENGINE=InnoDB;

ALTER TABLE bale_commitment
    ADD COLUMN temporary_handover_activation_id BINARY(16) NULL,
    ADD KEY ix_bale_temp_handover_activation(temporary_handover_activation_id,bale_id),
    ADD CONSTRAINT fk_bale_temp_handover_activation FOREIGN KEY(temporary_handover_activation_id)
        REFERENCES temporary_handover_activation(ticket_id);

ALTER TABLE bale_commitment DROP CHECK ck_commitment_owner,
    ADD CONSTRAINT ck_commitment_owner CHECK (
      (temporary_handover_activation_id IS NOT NULL AND state='TEMP_HANDOVER_RESERVED'
       AND source_scope_id IS NOT NULL AND source_evidence_set_id IS NOT NULL AND submission_id IS NULL
       AND order_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL
       AND temporary_continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL
       AND pc_disposition_id IS NULL AND disposition_id IS NULL)
      OR (temporary_handover_activation_id IS NULL AND (
        (temporary_continuation_id IS NOT NULL AND state='TEMP_CONTINUATION_RESERVED'
         AND source_scope_id IS NOT NULL AND source_evidence_set_id IS NOT NULL AND submission_id IS NULL
         AND order_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL
         AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL)
        OR (temporary_continuation_id IS NULL AND source_evidence_set_id IS NULL AND order_id IS NOT NULL AND (
          (state IN ('REVIEW_RESERVED','DISPATCHED') AND submission_id IS NOT NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL) OR
          (state='RECOVERY_RESERVED' AND submission_id IS NULL AND recovery_package_id IS NOT NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL) OR
          (state='CONTINUATION_RESERVED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NOT NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL) OR
          (state='QUARANTINED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NOT NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL) OR
          (state IN ('PC_RESERVED','DISPATCHED') AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NOT NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL) OR
          (state='QUARANTINED' AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NOT NULL AND disposition_id IS NULL))) OR
        (state='SOURCE_RESERVED' AND source_scope_id IS NOT NULL AND source_evidence_set_id IS NOT NULL AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND temporary_continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NULL) OR
        (state='QUARANTINED' AND source_scope_id IS NOT NULL AND source_evidence_set_id IS NOT NULL AND submission_id IS NULL AND recovery_package_id IS NULL AND continuation_id IS NULL AND temporary_continuation_id IS NULL AND disposition_evidence_id IS NULL AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND disposition_id IS NOT NULL)
      )));

DELIMITER $$
CREATE TRIGGER temp_handover_activation_binding BEFORE INSERT ON temporary_handover_activation FOR EACH ROW
BEGIN
    DECLARE bound_count INT DEFAULT 0;
    SELECT COUNT(*) INTO bound_count FROM temporary_handover_activation_ticket t
    JOIN handover_case h ON h.id=t.case_id AND h.scope_id=t.scope_id AND h.submission_id=t.submission_id
      AND h.canonical_order_id=t.canonical_order_id AND h.status='TARGET_PREPARED'
    JOIN submission_scope c ON c.id=t.scope_id AND c.work_round=t.from_work_round
      AND c.current_submission_id=t.submission_id AND c.canonical_order_id=t.canonical_order_id
    JOIN submission s ON s.id=t.submission_id AND s.scope_id=t.scope_id AND s.work_round=t.from_work_round
      AND s.status='ABORTED' AND s.temporary_authorization_id=t.source_authorization_id
    JOIN temporary_handover_confirmation f ON f.case_id=t.case_id AND f.id=NEW.confirmation_id
      AND f.authorization_id=t.target_authorization_id AND f.actor_id=t.target_user_id
      AND f.device_id=t.target_device_id AND f.session_id=NEW.target_session_id
    JOIN temporary_authorization parent ON parent.id=t.target_authorization_id
      AND parent.device_id=t.target_device_id AND parent.user_id=t.target_user_id AND parent.warehouse_id=h.warehouse_id
    JOIN temporary_authorization a ON a.id=NEW.new_authorization_id AND a.device_id=t.target_device_id
      AND a.user_id=t.target_user_id AND a.warehouse_id=h.warehouse_id AND a.recovery_epoch=t.source_recovery_epoch
      AND a.user_auth_version=t.target_user_auth_version AND a.device_version=t.target_device_version
      AND a.expires_at<=parent.expires_at AND a.issued_at>=t.created_at-INTERVAL 1 SECOND
      AND parent.expires_at>NEW.activated_at
    JOIN temporary_work_origin o ON o.creator_device_id=a.device_id AND o.authorization_id=a.id
      AND o.client_order_id=NEW.new_client_order_id
    WHERE t.id=NEW.ticket_id AND t.from_work_round=NEW.from_work_round AND t.to_work_round=NEW.to_work_round
      AND t.target_device_id=NEW.target_device_id AND t.target_user_id=NEW.target_user_id
      AND t.input_high_watermark<9223372036854775807 AND NEW.start_order_scan_seq=t.input_high_watermark+1
      AND NEW.retained_count=(SELECT COUNT(*) FROM temporary_handover_activation_owner b WHERE b.ticket_id=t.id);
    IF bound_count<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP handover activation binding is invalid'; END IF;
END$$
CREATE TRIGGER temp_handover_ticket_no_update BEFORE UPDATE ON temporary_handover_activation_ticket FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP handover activation tickets are immutable'; END$$
CREATE TRIGGER temp_handover_ticket_no_delete BEFORE DELETE ON temporary_handover_activation_ticket FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP handover activation tickets are retained'; END$$
CREATE TRIGGER temp_handover_owner_no_update BEFORE UPDATE ON temporary_handover_activation_owner FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP handover owner proofs are immutable'; END$$
CREATE TRIGGER temp_handover_owner_no_delete BEFORE DELETE ON temporary_handover_activation_owner FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP handover owner proofs are retained'; END$$
CREATE TRIGGER temp_handover_activation_no_update BEFORE UPDATE ON temporary_handover_activation FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP handover activations are immutable'; END$$
CREATE TRIGGER temp_handover_activation_no_delete BEFORE DELETE ON temporary_handover_activation FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP handover activations are retained'; END$$
DELIMITER ;
