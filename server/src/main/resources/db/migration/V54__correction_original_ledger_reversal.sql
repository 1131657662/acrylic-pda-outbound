-- Append references only. Original postings and historical correction rows remain immutable.
SET SESSION lock_wait_timeout=10;

ALTER TABLE correction_ledger
    DROP CHECK correction_ledger_chk_1,
    ADD COLUMN reverses_inventory_entry_id BINARY(16) NULL,
    ADD COLUMN restores_entry_id BINARY(16) NULL,
    ADD CONSTRAINT uq_correction_original_reversal UNIQUE(reverses_inventory_entry_id),
    ADD CONSTRAINT uq_correction_withdrawal_restore UNIQUE(restores_entry_id),
    ADD CONSTRAINT fk_correction_original_reversal FOREIGN KEY(reverses_inventory_entry_id) REFERENCES inventory_ledger(id),
    ADD CONSTRAINT fk_correction_withdrawal_restore FOREIGN KEY(restores_entry_id) REFERENCES correction_ledger(id),
    ADD CONSTRAINT ck_correction_posting_kind CHECK(entry_kind IN ('REVERSAL','WEIGHT_DIFF','MANUAL_DEDUCT','WITHDRAWAL_RESTORE')),
    ADD CONSTRAINT ck_correction_single_reference CHECK(
        (reverses_entry_id IS NOT NULL)+(reverses_inventory_entry_id IS NOT NULL)+(restores_entry_id IS NOT NULL)<=1),
    ADD CONSTRAINT ck_correction_reference_kind CHECK(
        (reverses_entry_id IS NULL AND reverses_inventory_entry_id IS NULL OR entry_kind='REVERSAL')
        AND (restores_entry_id IS NULL OR entry_kind='WITHDRAWAL_RESTORE')
        AND (entry_kind<>'WITHDRAWAL_RESTORE' OR restores_entry_id IS NOT NULL));
