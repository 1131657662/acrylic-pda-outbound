SET SESSION lock_wait_timeout=10;
-- A frozen round has one immutable identity, including a control head received before its body.
ALTER TABLE submission ADD UNIQUE KEY uq_submission_round(scope_id,work_round);
