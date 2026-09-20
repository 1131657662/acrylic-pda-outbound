SET SESSION lock_wait_timeout=10;

-- V24 is immutable. A worker only reads registered job types, including when recovering expired leases.
-- MySQL 8.0 rejects ALGORITHM=INPLACE for this mixed index/check alteration, so use its
-- version-specific default after the metadata lock timeout above has been applied.
ALTER TABLE background_job
    ADD KEY ix_background_job_type_queue(job_type,status,available_at,id),
    ADD KEY ix_background_job_type_lease(job_type,status,lease_until,id),
    ADD CONSTRAINT ck_background_job_pending_attempts
        CHECK(status NOT IN ('PENDING','RETRY') OR attempt_count<max_attempts),
    ADD CONSTRAINT ck_background_job_started
        CHECK(status NOT IN ('RUNNING','SUCCEEDED','FAILED') OR attempt_count>0);
