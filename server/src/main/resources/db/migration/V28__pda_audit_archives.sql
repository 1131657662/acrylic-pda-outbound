SET SESSION lock_wait_timeout=10;
CREATE TABLE pda_audit_archive (
 recovery_epoch BIGINT NOT NULL, origin_device_id BINARY(16) NOT NULL,
 after_seq BIGINT NOT NULL, through_seq BIGINT NOT NULL,
 payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 proof_text MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 PRIMARY KEY(recovery_epoch,origin_device_id,after_seq),
 FOREIGN KEY(origin_device_id) REFERENCES device(id) ON DELETE RESTRICT,
 CHECK(recovery_epoch>0), CHECK(after_seq>=0), CHECK(through_seq>after_seq AND through_seq-after_seq<=200)
) ENGINE=InnoDB;
