SET SESSION lock_wait_timeout=10;

-- A completion proof is valid only for the activated ticket's next round.  The
-- application store performs the same checks; this trigger keeps a direct SQL
-- writer from manufacturing a terminal state with an unrelated submission or
-- an owner that was never settled by the new round.
ALTER TABLE submission
    ADD UNIQUE KEY uq_submission_id_scope(id,scope_id);

DELIMITER $$
CREATE TRIGGER temp_source_continuation_closure_completion_guard
BEFORE INSERT ON temporary_source_continuation_closure FOR EACH ROW
BEGIN
    DECLARE target_count INT DEFAULT 0;
    DECLARE owner_total INT DEFAULT 0;
    DECLARE owner_settled INT DEFAULT 0;

    IF NEW.status='COMPLETED' THEN
        SELECT COUNT(*) INTO target_count
        FROM temporary_source_continuation t
        JOIN submission s ON s.id=NEW.target_submission_id
            AND s.scope_id=NEW.scope_id
            AND s.work_round=t.to_work_round
            AND s.temporary_authorization_id=NEW.new_authorization_id
            AND s.status='SUCCEEDED'
            AND s.receipt_text=NEW.receipt_text
        JOIN submission_scope c ON c.id=s.scope_id AND c.canonical_order_id IS NOT NULL
        WHERE t.id=NEW.ticket_id AND t.scope_id=NEW.scope_id
            AND t.from_work_round=NEW.from_work_round
            AND t.status='ACTIVATED' AND t.new_authorization_id=NEW.new_authorization_id;
        IF target_count<>1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation completion target is not settled';
        END IF;

        SELECT COUNT(*), COALESCE(SUM(CASE WHEN k.state='DISPATCHED'
            AND k.submission_id=NEW.target_submission_id
            AND k.order_id=(SELECT canonical_order_id FROM submission_scope WHERE id=NEW.scope_id)
            AND k.temporary_continuation_id IS NULL
            AND k.source_evidence_set_id IS NULL
            AND k.dispatch_cycle=r.expected_cycle THEN 1 ELSE 0 END),0)
            INTO owner_total,owner_settled
        FROM temporary_source_continuation_bale r
        LEFT JOIN bale_commitment k ON k.bale_id=r.bale_id
        WHERE r.continuation_id=NEW.ticket_id AND r.scope_id=NEW.scope_id
            AND r.from_work_round=NEW.from_work_round;
        IF owner_total=0 OR owner_total<>owner_settled THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TEMP continuation completion owners are not settled';
        END IF;
    END IF;
END$$
DELIMITER ;
