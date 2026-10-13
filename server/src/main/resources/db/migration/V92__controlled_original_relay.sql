SET SESSION lock_wait_timeout=10;
CREATE TABLE original_relay (
 id BINARY(16) NOT NULL PRIMARY KEY, export_id BINARY(16) NOT NULL,
 warehouse_id BINARY(16) NOT NULL, original_device_id BINARY(16) NOT NULL,
 original_operator_id BINARY(16) NOT NULL, relay_operator_id BINARY(16) NOT NULL,
 archive_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 device_key_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 local_order_id BINARY(16) NOT NULL, source_trust VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 received_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_original_relay_export(export_id),
 FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), FOREIGN KEY(relay_operator_id) REFERENCES app_user(id),
 CHECK(archive_sha256 REGEXP '^[0-9a-f]{64}$'), CHECK(device_key_sha256 REGEXP '^[0-9a-f]{64}$'),
 CHECK(source_kind IN ('FORMAL','TEMP','INDEPENDENT')), CHECK(source_trust IN ('TRUSTED','UNVERIFIED'))
) ENGINE=InnoDB;
-- Original identity is a signed claim: unknown device/person evidence may be retained,
-- but the business operation separately requires the actual registered identity.
CREATE TABLE original_relay_result (
 id BINARY(16) NOT NULL PRIMARY KEY, relay_id BINARY(16) NOT NULL,
 relay_operator_id BINARY(16) NOT NULL, result_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 receipt_text MEDIUMTEXT NOT NULL, created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_original_relay_result(relay_id,result_sha256),
 FOREIGN KEY(relay_id) REFERENCES original_relay(id), FOREIGN KEY(relay_operator_id) REFERENCES app_user(id),
 CHECK(result_sha256 REGEXP '^[0-9a-f]{64}$'), CHECK(OCTET_LENGTH(receipt_text) BETWEEN 1 AND 2097152)
) ENGINE=InnoDB;
INSERT INTO permission(id,code,name)
 VALUES(UUID_TO_BIN('019a0000-0000-7000-8000-000000000103'),'rescue:relay','同仓受控中转原设备冻结资料');
INSERT INTO role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM role r CROSS JOIN permission p WHERE r.code='SUPERVISOR' AND p.code='rescue:relay';
DELIMITER $$
CREATE TRIGGER original_relay_no_update BEFORE UPDATE ON original_relay FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ORIGINAL_RELAY_IMMUTABLE'; END$$
CREATE TRIGGER original_relay_no_delete BEFORE DELETE ON original_relay FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ORIGINAL_RELAY_IMMUTABLE'; END$$
CREATE TRIGGER original_relay_result_no_update BEFORE UPDATE ON original_relay_result FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ORIGINAL_RELAY_RESULT_IMMUTABLE'; END$$
CREATE TRIGGER original_relay_result_no_delete BEFORE DELETE ON original_relay_result FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ORIGINAL_RELAY_RESULT_IMMUTABLE'; END$$
DELIMITER ;
