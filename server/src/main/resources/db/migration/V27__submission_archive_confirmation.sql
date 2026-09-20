SET SESSION lock_wait_timeout=10;
-- Durable server copies only. This confirmation does not authorize PDA fact deletion.
CREATE TABLE submission_archive (
 submission_id BINARY(16) NOT NULL, recovery_epoch BIGINT NOT NULL,
 payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 receipt_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 confirmation_text MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 archived_at DATETIME(3) NOT NULL, retain_until DATETIME(3) NOT NULL,
 PRIMARY KEY(submission_id,recovery_epoch),
 FOREIGN KEY(submission_id) REFERENCES submission(id) ON DELETE RESTRICT,
 CHECK(recovery_epoch>0), CHECK(retain_until>=DATE_ADD(archived_at,INTERVAL 5 YEAR))
) ENGINE=InnoDB;
