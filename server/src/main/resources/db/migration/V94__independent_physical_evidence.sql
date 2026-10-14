SET SESSION lock_wait_timeout=10;
CREATE TABLE independent_physical_attachment (
 id BINARY(16) NOT NULL PRIMARY KEY, relay_id BINARY(16) NOT NULL,
 warehouse_id BINARY(16) NOT NULL, uploaded_by BINARY(16) NOT NULL,
 content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 size_bytes INT NOT NULL, content_bytes MEDIUMBLOB NOT NULL, created_at DATETIME(3) NOT NULL,
 FOREIGN KEY(relay_id) REFERENCES original_relay(id), FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),
 FOREIGN KEY(uploaded_by) REFERENCES app_user(id),
 CHECK(size_bytes BETWEEN 1 AND 10485760), CHECK(size_bytes=OCTET_LENGTH(content_bytes)),
 CHECK(content_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB;
CREATE TABLE independent_physical_check (
 id BINARY(16) NOT NULL PRIMARY KEY, relay_id BINARY(16) NOT NULL,
 warehouse_id BINARY(16) NOT NULL, supervisor_id BINARY(16) NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_text MEDIUMTEXT NOT NULL, context_text MEDIUMTEXT NOT NULL, receipt_text MEDIUMTEXT NOT NULL,
 recorded_at DATETIME(3) NOT NULL,
 FOREIGN KEY(relay_id) REFERENCES original_relay(id), FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),
 FOREIGN KEY(supervisor_id) REFERENCES app_user(id),
 CHECK(request_sha256 REGEXP '^[0-9a-f]{64}$'), CHECK(context_sha256 REGEXP '^[0-9a-f]{64}$'),
 CHECK(JSON_VALID(request_text)), CHECK(JSON_VALID(context_text)), CHECK(JSON_VALID(receipt_text))
) ENGINE=InnoDB;
CREATE TABLE independent_physical_attachment_use (
 check_id BINARY(16) NOT NULL, attachment_id BINARY(16) NOT NULL,
 PRIMARY KEY(check_id,attachment_id), FOREIGN KEY(check_id) REFERENCES independent_physical_check(id),
 FOREIGN KEY(attachment_id) REFERENCES independent_physical_attachment(id)
) ENGINE=InnoDB;
DELIMITER $$
CREATE TRIGGER independent_physical_attachment_no_update BEFORE UPDATE ON independent_physical_attachment FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Independent physical attachment is immutable'; END$$
CREATE TRIGGER independent_physical_attachment_no_delete BEFORE DELETE ON independent_physical_attachment FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Independent physical attachment must be retained'; END$$
CREATE TRIGGER independent_physical_check_no_update BEFORE UPDATE ON independent_physical_check FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Independent physical check is immutable'; END$$
CREATE TRIGGER independent_physical_check_no_delete BEFORE DELETE ON independent_physical_check FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Independent physical check must be retained'; END$$
CREATE TRIGGER independent_physical_use_no_update BEFORE UPDATE ON independent_physical_attachment_use FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Independent physical reference is immutable'; END$$
CREATE TRIGGER independent_physical_use_no_delete BEFORE DELETE ON independent_physical_attachment_use FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Independent physical reference must be retained'; END$$
DELIMITER ;
