-- R07-P08-A：更正事实与命令回执的 append-only 保护。
-- 生命周期字段仍允许由已实现状态机推进；原来源、目标、摘要、请求人、逐笔效果、账务和回执一旦写入不得改写或删除。

DELIMITER $$

CREATE TRIGGER correction_document_no_delete BEFORE DELETE ON correction_document FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction document is retained';
END$$

CREATE TRIGGER correction_document_facts_immutable BEFORE UPDATE ON correction_document FOR EACH ROW
BEGIN
    IF NOT (OLD.warehouse_id <=> NEW.warehouse_id)
       OR NOT (OLD.order_id <=> NEW.order_id)
       OR NOT (OLD.type <=> NEW.type)
       OR NOT (OLD.expected_result_version <=> NEW.expected_result_version)
       OR NOT (OLD.reason <=> NEW.reason)
       OR NOT (OLD.document_sha256 <=> NEW.document_sha256)
       OR NOT (OLD.source_recovery_epoch <=> NEW.source_recovery_epoch)
       OR NOT (OLD.requested_by <=> NEW.requested_by)
       OR NOT (OLD.requested_at <=> NEW.requested_at) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction document facts are immutable';
    END IF;
    IF OLD.approved_by IS NOT NULL AND NOT (OLD.approved_by <=> NEW.approved_by) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction approval is append-only';
    END IF;
    IF OLD.approved_at IS NOT NULL AND NOT (OLD.approved_at <=> NEW.approved_at) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction approval time is append-only';
    END IF;
    IF OLD.effective_result_version IS NOT NULL AND NOT (OLD.effective_result_version <=> NEW.effective_result_version) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction effective version is append-only';
    END IF;
    IF OLD.withdrawal_requested_by IS NOT NULL AND NOT (OLD.withdrawal_requested_by <=> NEW.withdrawal_requested_by) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction withdrawal requester is append-only';
    END IF;
    IF OLD.withdrawal_requested_at IS NOT NULL AND NOT (OLD.withdrawal_requested_at <=> NEW.withdrawal_requested_at) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction withdrawal request time is append-only';
    END IF;
    IF OLD.withdrawal_confirmed_by IS NOT NULL AND NOT (OLD.withdrawal_confirmed_by <=> NEW.withdrawal_confirmed_by) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction withdrawal confirmer is append-only';
    END IF;
    IF OLD.withdrawal_confirmed_at IS NOT NULL AND NOT (OLD.withdrawal_confirmed_at <=> NEW.withdrawal_confirmed_at) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction withdrawal confirmation time is append-only';
    END IF;
    IF OLD.status='REJECTED' AND NEW.status<>'REJECTED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Rejected correction cannot be reopened';
    END IF;
    IF OLD.status='WITHDRAW_PENDING' AND NEW.status<>'WITHDRAWN' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Pending correction withdrawal can only be confirmed';
    END IF;
    IF OLD.status='WITHDRAWN' AND NEW.status<>'WITHDRAWN' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Withdrawn correction cannot be reopened';
    END IF;
END$$

CREATE TRIGGER correction_line_no_update BEFORE UPDATE ON correction_line FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction line is append-only';
END$$
CREATE TRIGGER correction_line_no_delete BEFORE DELETE ON correction_line FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction line is retained';
END$$

CREATE TRIGGER correction_effect_no_update BEFORE UPDATE ON correction_effect FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction effect is append-only';
END$$
CREATE TRIGGER correction_effect_no_delete BEFORE DELETE ON correction_effect FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction effect is retained';
END$$

CREATE TRIGGER correction_ledger_no_update BEFORE UPDATE ON correction_ledger FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction ledger is append-only';
END$$
CREATE TRIGGER correction_ledger_no_delete BEFORE DELETE ON correction_ledger FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction ledger is retained';
END$$

CREATE TRIGGER cycle_reopening_no_update BEFORE UPDATE ON cycle_reopening FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cycle reopening is append-only';
END$$
CREATE TRIGGER cycle_reopening_no_delete BEFORE DELETE ON cycle_reopening FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cycle reopening is retained';
END$$

CREATE TRIGGER correction_receipt_no_update BEFORE UPDATE ON correction_command_receipt FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction command receipt is immutable';
END$$
CREATE TRIGGER correction_receipt_no_delete BEFORE DELETE ON correction_command_receipt FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction command receipt is retained';
END$$

CREATE TRIGGER reconciliation_no_delete BEFORE DELETE ON inventory_reconciliation FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Inventory reconciliation is retained';
END$$

CREATE TRIGGER reconciliation_facts_immutable BEFORE UPDATE ON inventory_reconciliation FOR EACH ROW
BEGIN
    IF NOT (OLD.warehouse_id <=> NEW.warehouse_id)
       OR NOT (OLD.kind <=> NEW.kind)
       OR NOT (OLD.bale_id <=> NEW.bale_id)
       OR NOT (OLD.dispatch_id <=> NEW.dispatch_id)
       OR NOT (OLD.cycle <=> NEW.cycle)
       OR NOT (OLD.expected_result_version <=> NEW.expected_result_version)
       OR NOT (OLD.expected_ledger_version <=> NEW.expected_ledger_version)
       OR NOT (OLD.expected_stock_state_version <=> NEW.expected_stock_state_version)
       OR NOT (OLD.source_correction_id <=> NEW.source_correction_id)
       OR NOT (OLD.command_receipt_id <=> NEW.command_receipt_id)
       OR NOT (OLD.created_at <=> NEW.created_at) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Inventory reconciliation facts are immutable';
    END IF;
END$$

DELIMITER ;
