-- Prepared target-35 negative cases. Each statement must run in its own rollback transaction.
-- This file is intentionally inert until the guarded runner and W1 SHA are scheduled.
SET SESSION lock_wait_timeout=10;
-- Reference-shape: OPEN with object, PAGE/STOP with set, VERIFY without verification.
-- Context: cross-scope, cross-submission, and cross-round composite FK references.
-- Immutability: UPDATE/DELETE source_evidence_intake_receipt.
-- Domain: route/status/operation/digest/size violations.
