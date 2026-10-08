SET SESSION lock_wait_timeout=10;

-- E2A receipt creator_device_id is the actual submission freeze device.
-- client_order_id retains the immutable scope client (including legacy R06 receipts).
-- The existing submission/auth/device/operator/epoch FK still binds that device.
-- Scope ownership remains in submission_scope; no original bytes or roots change.
ALTER TABLE submission_scope ADD UNIQUE KEY uq_scope_client_context(id,client_order_id);
SET @e2a_scope_fk=(SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
    WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='source_evidence_intake_receipt'
      AND COLUMN_NAME='creator_device_id' AND REFERENCED_TABLE_NAME='submission_scope');
SET @e2a_drop_fk=CONCAT('ALTER TABLE source_evidence_intake_receipt DROP FOREIGN KEY `',@e2a_scope_fk,'`');
PREPARE e2a_drop FROM @e2a_drop_fk;
EXECUTE e2a_drop;
DEALLOCATE PREPARE e2a_drop;
ALTER TABLE source_evidence_intake_receipt ADD CONSTRAINT fk_e2a_receipt_scope_client
    FOREIGN KEY(scope_id,client_order_id) REFERENCES submission_scope(id,client_order_id);
