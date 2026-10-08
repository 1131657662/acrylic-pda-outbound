SET SESSION lock_wait_timeout=10;

-- The immutable scope keeps the original creator. A receiving command retains
-- its own work origin and is additionally bound to the independent activation.
SET @handover_scope_fk=(SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
    WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='temporary_command_receipt'
      AND COLUMN_NAME='creator_device_id' AND REFERENCED_TABLE_NAME='submission_scope');
SET @handover_drop_fk=CONCAT('ALTER TABLE temporary_command_receipt DROP FOREIGN KEY `',@handover_scope_fk,'`');
PREPARE handover_drop FROM @handover_drop_fk;
EXECUTE handover_drop;
DEALLOCATE PREPARE handover_drop;
ALTER TABLE temporary_command_receipt ADD CONSTRAINT fk_temp_command_work_origin
    FOREIGN KEY(creator_device_id,client_order_id) REFERENCES temporary_work_origin(creator_device_id,client_order_id);

DELIMITER $$
CREATE TRIGGER temp_command_origin_binding BEFORE INSERT ON temporary_command_receipt FOR EACH ROW
BEGIN
    DECLARE valid_context INT DEFAULT 0;
    SELECT COUNT(*) INTO valid_context FROM submission s JOIN submission_scope c ON c.id=s.scope_id
    JOIN temporary_authorization a ON a.id=s.temporary_authorization_id
      AND a.device_id=s.freeze_device_id AND a.user_id=s.operator_id AND a.warehouse_id=c.warehouse_id
    WHERE s.id=NEW.submission_id AND c.id=NEW.scope_id AND c.warehouse_id=NEW.warehouse_id
      AND s.work_round=NEW.work_round AND s.temporary_authorization_id=NEW.temporary_authorization_id
      AND s.freeze_device_id=NEW.creator_device_id AND s.operator_id=NEW.original_operator_id
      AND s.source_recovery_epoch=NEW.source_recovery_epoch
      AND ((NEW.creator_device_id=c.creator_device_id AND NEW.client_order_id=c.client_order_id
        AND (s.work_round=1 OR EXISTS(SELECT 1 FROM temporary_source_continuation_submission r
          JOIN temporary_source_continuation_activation n ON n.ticket_id=r.ticket_id AND n.scope_id=r.scope_id
            AND n.from_work_round=r.from_work_round AND n.new_authorization_id=a.id
          WHERE r.scope_id=c.id AND r.submission_id=s.id AND r.to_work_round=s.work_round
            AND r.from_work_round=s.work_round-1 AND n.target_device_id=a.device_id AND n.target_operator_id=a.user_id)))
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
