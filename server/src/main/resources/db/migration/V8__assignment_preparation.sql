SET SESSION lock_wait_timeout=10;
CREATE TABLE assignment_preparation (
 id BINARY(16) PRIMARY KEY, assignment_id BINARY(16) NOT NULL UNIQUE,
 manifest_text MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 manifest_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 signed_manifest TEXT CHARACTER SET ascii NOT NULL,
 plan_gzip MEDIUMBLOB NOT NULL, inventory_gzip MEDIUMBLOB NOT NULL, availability_gzip MEDIUMBLOB NOT NULL,
 created_at DATETIME(3) NOT NULL,
 FOREIGN KEY(assignment_id) REFERENCES order_assignment(id)
) ENGINE=InnoDB;
