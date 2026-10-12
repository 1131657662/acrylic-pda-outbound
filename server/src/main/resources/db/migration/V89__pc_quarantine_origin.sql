-- Preserve TEMP foreign keys while adding a separately typed PC evidence origin.
SET SESSION lock_wait_timeout=10;
ALTER TABLE quarantine_origin
    DROP FOREIGN KEY quarantine_origin_ibfk_2,
    DROP FOREIGN KEY quarantine_origin_ibfk_3,
    MODIFY disposition_id BINARY(16) NULL,
    MODIFY original_receipt_id BINARY(16) NULL,
    ADD pc_disposition_id BINARY(16) NULL,
    ADD pc_evidence_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD UNIQUE KEY uq_quarantine_pc_origin(pc_disposition_id,bale_id),
    ADD CONSTRAINT fk_quarantine_temp_origin FOREIGN KEY(disposition_id,bale_id) REFERENCES temporary_physical_disposition_item(disposition_id,bale_id),
    ADD CONSTRAINT fk_quarantine_temp_receipt FOREIGN KEY(original_receipt_id) REFERENCES temporary_command_receipt(id),
    ADD CONSTRAINT fk_quarantine_pc_origin FOREIGN KEY(pc_disposition_id) REFERENCES pc_disposition(id),
    ADD CONSTRAINT ck_quarantine_origin_type CHECK (
      (disposition_id IS NOT NULL AND original_receipt_id IS NOT NULL AND pc_disposition_id IS NULL AND pc_evidence_sha256 IS NULL) OR
      (disposition_id IS NULL AND original_receipt_id IS NULL AND pc_disposition_id IS NOT NULL
       AND pc_evidence_sha256 IS NOT NULL AND pc_evidence_sha256 REGEXP '^[a-f0-9]{64}$'));
