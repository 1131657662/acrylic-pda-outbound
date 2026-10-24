SET SESSION lock_wait_timeout=10;

-- Display evidence is frozen at issuance. Historical rows intentionally remain NULL;
-- do not backfill using account names observed after the original credential was issued.
ALTER TABLE temporary_authorization
    ADD COLUMN operator_name_proof TEXT CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD CONSTRAINT ck_temporary_name_proof_bytes CHECK
        (operator_name_proof IS NULL OR OCTET_LENGTH(operator_name_proof) BETWEEN 1 AND 16384);

ALTER TABLE independent_capture_credential
    ADD COLUMN operator_name_proof TEXT CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD CONSTRAINT ck_independent_name_proof_bytes CHECK
        (operator_name_proof IS NULL OR OCTET_LENGTH(operator_name_proof) BETWEEN 1 AND 16384);
