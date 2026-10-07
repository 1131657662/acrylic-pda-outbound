SET SESSION lock_wait_timeout=10;

-- Preserve existing cases, evidence, receipts and all activation constraints.
-- TEMP uses its own verified E2A STOP; this does not issue target or scan authority.
ALTER TABLE handover_case
    DROP CHECK handover_case_chk_1,
    ADD CONSTRAINT ck_handover_source_kind CHECK (source_kind IN ('FORMAL','TEMP'));
