SET SESSION lock_wait_timeout=10;

CREATE TABLE background_job (
    id BINARY(16) PRIMARY KEY,
    job_type VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    dedupe_key VARCHAR(180) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload JSON NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL,
    available_at DATETIME(3) NOT NULL,
    lease_owner VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NULL,
    lease_token BINARY(16) NULL,
    lease_until DATETIME(3) NULL,
    last_error VARCHAR(2000) CHARACTER SET utf8mb4 NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_background_job_dedupe(dedupe_key),
    KEY ix_background_job_queue(status,available_at,id),
    KEY ix_background_job_lease(status,lease_until),
    CHECK(job_type REGEXP '^[A-Z][A-Z0-9_.:-]{0,79}$'),
    CHECK(dedupe_key <> ''),
    CHECK(status IN ('PENDING','RUNNING','RETRY','SUCCEEDED','FAILED')),
    CHECK(attempt_count >= 0 AND attempt_count <= max_attempts),
    CHECK(max_attempts BETWEEN 1 AND 20),
    CHECK((status='RUNNING' AND lease_owner IS NOT NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
       OR (status<>'RUNNING' AND lease_owner IS NULL AND lease_token IS NULL AND lease_until IS NULL)),
    CHECK(status IN ('PENDING','RETRY') OR available_at IS NOT NULL)
) ENGINE=InnoDB;
