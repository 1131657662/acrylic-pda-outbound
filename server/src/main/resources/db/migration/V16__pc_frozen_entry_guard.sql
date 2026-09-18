SET SESSION lock_wait_timeout=10;
CREATE TABLE pc_submission_entry (
 submission_id BINARY(16) NOT NULL, entry_id BINARY(16) NOT NULL, frozen_status VARCHAR(12) NOT NULL,
 record_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 PRIMARY KEY(submission_id,entry_id), KEY ix_pc_frozen_entry(entry_id,frozen_status),
 FOREIGN KEY(submission_id) REFERENCES pc_submission(id), FOREIGN KEY(entry_id) REFERENCES pc_entry(id),
 CHECK(frozen_status IN ('ACTIVE','VOIDED'))
) ENGINE=InnoDB;
