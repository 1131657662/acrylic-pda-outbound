SET SESSION lock_wait_timeout=10;

-- Preserve the immutable scope origin and bind receiving R06 commands to their
-- own activation. Existing rows and append-only receipt guards stay unchanged.
DROP TRIGGER temp_command_origin_binding;
DELIMITER $$
CREATE TRIGGER temp_command_origin_binding BEFORE INSERT ON temporary_command_receipt FOR EACH ROW
BEGIN
    DECLARE valid_context INT DEFAULT 0;
    SELECT COUNT(*) INTO valid_context FROM submission s JOIN submission_scope c ON c.id=s.scope_id
    JOIN temporary_authorization a ON a.id=s.temporary_authorization_id
      AND a.device_id=s.freeze_device_id AND a.user_id=s.operator_id AND a.warehouse_id=c.warehouse_id
      AND a.recovery_epoch=s.source_recovery_epoch
    WHERE s.id=NEW.submission_id AND c.id=NEW.scope_id AND c.warehouse_id=NEW.warehouse_id
      AND s.work_round=NEW.work_round AND s.temporary_authorization_id=NEW.temporary_authorization_id
      AND s.freeze_device_id=NEW.creator_device_id AND s.operator_id=NEW.original_operator_id
      AND s.source_recovery_epoch=NEW.source_recovery_epoch
      AND NOT (EXISTS(SELECT 1 FROM temporary_source_continuation_submission r WHERE r.submission_id=s.id)
        AND EXISTS(SELECT 1 FROM temporary_handover_submission r WHERE r.submission_id=s.id))
      AND ((NEW.creator_device_id=c.creator_device_id AND NEW.client_order_id=c.client_order_id
        AND (s.work_round=1 OR EXISTS(SELECT 1 FROM temporary_source_continuation_submission r
          JOIN temporary_source_continuation_activation n ON n.ticket_id=r.ticket_id AND n.scope_id=r.scope_id
            AND n.from_work_round=r.from_work_round AND n.new_authorization_id=a.id
          WHERE r.scope_id=c.id AND r.submission_id=s.id AND r.to_work_round=s.work_round
            AND r.from_work_round=s.work_round-1 AND n.target_device_id=a.device_id AND n.target_operator_id=a.user_id)))
        OR EXISTS(SELECT 1 FROM temporary_source_continuation_submission r
          JOIN temporary_source_continuation_activation n ON n.ticket_id=r.ticket_id AND n.scope_id=r.scope_id
            AND n.from_work_round=r.from_work_round AND n.new_authorization_id=a.id
          JOIN temporary_work_origin o ON o.creator_device_id=n.target_device_id
            AND o.client_order_id=n.new_client_order_id AND o.authorization_id=n.new_authorization_id
          WHERE r.scope_id=c.id AND r.submission_id=s.id AND r.to_work_round=s.work_round
            AND r.from_work_round=s.work_round-1 AND n.target_device_id=NEW.creator_device_id
            AND n.target_operator_id=NEW.original_operator_id AND n.new_client_order_id=NEW.client_order_id)
        OR EXISTS(SELECT 1 FROM temporary_handover_submission r
          JOIN temporary_handover_activation n ON n.ticket_id=r.ticket_id
          JOIN temporary_handover_activation_ticket t ON t.id=n.ticket_id AND t.scope_id=r.scope_id
          JOIN temporary_work_origin o ON o.creator_device_id=n.target_device_id
            AND o.client_order_id=n.new_client_order_id AND o.authorization_id=n.new_authorization_id
          WHERE r.scope_id=c.id AND r.submission_id=s.id AND r.to_work_round=s.work_round
            AND r.from_work_round=s.work_round-1 AND t.to_work_round=s.work_round
            AND n.new_authorization_id=a.id AND n.target_device_id=NEW.creator_device_id
            AND n.target_user_id=NEW.original_operator_id AND n.new_client_order_id=NEW.client_order_id));
    IF valid_context<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP command origin binding is invalid'; END IF;
END$$
DELIMITER ;
