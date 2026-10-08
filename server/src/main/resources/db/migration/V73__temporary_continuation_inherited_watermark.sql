SET SESSION lock_wait_timeout=10;

-- The cumulative order prefix is bound by an immutable receiving activation.
-- Only the new suffix is bounded by the current device counter; all identity,
-- STOP, original-payload and registration constraints remain enforced.
DROP TRIGGER temp_continuation_submission_insert_guard;
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
    LEFT JOIN temporary_handover_submission phr ON phr.submission_id=p.id AND phr.scope_id=p.scope_id AND phr.to_work_round=p.work_round
    LEFT JOIN temporary_handover_activation pha ON pha.ticket_id=phr.ticket_id AND pha.to_work_round=p.work_round
      AND pha.new_authorization_id=p.temporary_authorization_id AND pha.target_device_id=p.freeze_device_id AND pha.target_user_id=p.operator_id
    LEFT JOIN temporary_source_continuation_submission pr ON pr.submission_id=p.id AND pr.scope_id=p.scope_id AND pr.to_work_round=p.work_round
    LEFT JOIN temporary_source_continuation_activation pa ON pa.ticket_id=pr.ticket_id AND pa.scope_id=pr.scope_id AND pa.from_work_round=pr.from_work_round
      AND pa.new_authorization_id=p.temporary_authorization_id AND pa.target_device_id=p.freeze_device_id AND pa.target_operator_id=p.operator_id
    LEFT JOIN temporary_source_continuation pt ON pt.id=pa.ticket_id AND pt.scope_id=p.scope_id AND pt.to_work_round=p.work_round
    JOIN source_evidence_set e ON e.id=t.evidence_set_id AND e.scope_id=t.scope_id
      AND e.submission_id=p.id AND e.work_round=p.work_round
      AND e.order_scan_high_watermark>=0 AND e.order_scan_high_watermark<9223372036854775807
      AND e.order_scan_high_watermark>=COALESCE(pha.start_order_scan_seq,pt.next_order_scan_seq,1)-1
      AND e.order_scan_high_watermark-(COALESCE(pha.start_order_scan_seq,pt.next_order_scan_seq,1)-1)<=e.capture_high_watermark
      AND ((p.work_round=1 AND pha.ticket_id IS NULL AND pa.ticket_id IS NULL)
        OR (p.work_round>1 AND ((pha.ticket_id IS NOT NULL AND pa.ticket_id IS NULL)
          OR (pha.ticket_id IS NULL AND pa.ticket_id IS NOT NULL))))
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
DELIMITER ;
