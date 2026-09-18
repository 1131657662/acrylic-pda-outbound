SET SESSION lock_wait_timeout = 10;

ALTER TABLE offline_authorization
    DROP INDEX assignment_id,
    ADD COLUMN authorization_sequence BIGINT NOT NULL DEFAULT 0 AFTER assignment_id,
    ADD COLUMN replaced_by BINARY(16) NULL AFTER revoked_at,
    ADD UNIQUE KEY uq_offline_authorization_sequence(assignment_id,authorization_sequence),
    ADD FOREIGN KEY fk_offline_authorization_replaced_by(replaced_by) REFERENCES offline_authorization(id),
    ADD CONSTRAINT ck_offline_authorization_sequence CHECK(authorization_sequence>=0);
