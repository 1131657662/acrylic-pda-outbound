-- An application original proves physical effects independently of the final dispatch result.
SET SESSION lock_wait_timeout=10;
CREATE TABLE formal_disposition_application (
    submission_id BINARY(16) PRIMARY KEY,
    payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    dispositions_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    receipt_bytes LONGBLOB NOT NULL,
    receipt_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    applied_at DATETIME(3) NOT NULL,
    FOREIGN KEY(submission_id) REFERENCES submission(id),
    CHECK(payload_sha256 REGEXP '^[a-f0-9]{64}$' AND dispositions_sha256 REGEXP '^[a-f0-9]{64}$'
      AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND OCTET_LENGTH(receipt_bytes)>0)
) ENGINE=InnoDB;
DELIMITER $$
CREATE TRIGGER formal_disposition_application_no_update BEFORE UPDATE ON formal_disposition_application
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Original physical application receipt is immutable'; END$$
CREATE TRIGGER formal_disposition_application_no_delete BEFORE DELETE ON formal_disposition_application
FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Original physical application receipt must be retained'; END$$
DELIMITER ;
