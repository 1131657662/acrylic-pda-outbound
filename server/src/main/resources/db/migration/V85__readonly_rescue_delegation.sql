SET SESSION lock_wait_timeout=10;
CREATE TABLE rescue_request (
 id BINARY(16) NOT NULL PRIMARY KEY, warehouse_id BINARY(16) NOT NULL,
 device_id BINARY(16) NOT NULL, operator_id BINARY(16) NOT NULL, rescuer_id BINARY(16) NOT NULL,
 device_key_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 original_text TEXT NOT NULL, device_signature VARCHAR(256) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(3) NOT NULL,
 KEY ix_rescue_request_owner(device_id,operator_id,id), KEY ix_rescue_request_warehouse(warehouse_id,id),
 FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), FOREIGN KEY(device_id) REFERENCES device(id),
 FOREIGN KEY(operator_id) REFERENCES app_user(id), FOREIGN KEY(rescuer_id) REFERENCES app_user(id),
 CHECK(operator_id<>rescuer_id), CHECK(OCTET_LENGTH(original_text) BETWEEN 1 AND 32768),
 CHECK(device_key_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB;
CREATE TABLE rescue_request_scope (
 request_id BINARY(16) NOT NULL, ordinal INT NOT NULL,
 source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, local_order_id BINARY(16) NOT NULL,
 PRIMARY KEY(request_id,ordinal), UNIQUE KEY uq_rescue_request_target(request_id,source,local_order_id),
 FOREIGN KEY(request_id) REFERENCES rescue_request(id),
 CHECK(ordinal BETWEEN 1 AND 100), CHECK(source IN ('FORMAL','TEMP'))
) ENGINE=InnoDB;
CREATE TABLE rescue_grant (
 id BINARY(16) NOT NULL PRIMARY KEY, request_id BINARY(16) NOT NULL,
 issuer_id BINARY(16) NOT NULL, issued_at DATETIME(3) NOT NULL, expires_at DATETIME(3) NULL,
 original_token TEXT CHARACTER SET ascii NOT NULL, reason VARCHAR(1000) NOT NULL,
 UNIQUE KEY uq_rescue_grant_request(request_id),
 FOREIGN KEY(request_id) REFERENCES rescue_request(id), FOREIGN KEY(issuer_id) REFERENCES app_user(id),
 CHECK(expires_at IS NULL OR expires_at>issued_at), CHECK(CHAR_LENGTH(TRIM(reason))>0)
) ENGINE=InnoDB;
CREATE TABLE rescue_revocation (
 grant_id BINARY(16) NOT NULL PRIMARY KEY, issuer_id BINARY(16) NOT NULL,
 revoked_at DATETIME(3) NOT NULL, original_token TEXT CHARACTER SET ascii NOT NULL, reason VARCHAR(1000) NOT NULL,
 FOREIGN KEY(grant_id) REFERENCES rescue_grant(id), FOREIGN KEY(issuer_id) REFERENCES app_user(id),
 CHECK(CHAR_LENGTH(TRIM(reason))>0)
) ENGINE=InnoDB;
INSERT INTO permission(id,code,name) VALUES
 (UUID_TO_BIN('019a0000-0000-7000-8000-000000000101'),'rescue:authorize','预授权和撤销指定原件的只读救援'),
 (UUID_TO_BIN('019a0000-0000-7000-8000-000000000102'),'rescue:read','作为预先指定主管取回原件');
INSERT INTO role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM role r CROSS JOIN permission p
 WHERE r.code='SUPERVISOR' AND p.code IN ('rescue:authorize','rescue:read');

DELIMITER $$
CREATE TRIGGER rescue_request_no_update BEFORE UPDATE ON rescue_request FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RESCUE_REQUEST_IMMUTABLE'; END$$
CREATE TRIGGER rescue_request_no_delete BEFORE DELETE ON rescue_request FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RESCUE_REQUEST_IMMUTABLE'; END$$
CREATE TRIGGER rescue_scope_no_update BEFORE UPDATE ON rescue_request_scope FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RESCUE_SCOPE_IMMUTABLE'; END$$
CREATE TRIGGER rescue_scope_no_delete BEFORE DELETE ON rescue_request_scope FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RESCUE_SCOPE_IMMUTABLE'; END$$
CREATE TRIGGER rescue_scope_insert BEFORE INSERT ON rescue_request_scope FOR EACH ROW
BEGIN
 IF EXISTS(SELECT 1 FROM rescue_grant WHERE request_id=NEW.request_id) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RESCUE_SCOPE_ALREADY_AUTHORIZED';
 END IF;
END$$
CREATE TRIGGER rescue_grant_no_update BEFORE UPDATE ON rescue_grant FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RESCUE_GRANT_IMMUTABLE'; END$$
CREATE TRIGGER rescue_grant_no_delete BEFORE DELETE ON rescue_grant FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RESCUE_GRANT_IMMUTABLE'; END$$
CREATE TRIGGER rescue_grant_insert BEFORE INSERT ON rescue_grant FOR EACH ROW
BEGIN
 IF EXISTS(SELECT 1 FROM rescue_request WHERE id=NEW.request_id AND operator_id=NEW.issuer_id) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INDEPENDENT_RESCUE_APPROVAL_REQUIRED';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM rescue_request_scope WHERE request_id=NEW.request_id) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RESCUE_SCOPE_REQUIRED';
 END IF;
END$$
CREATE TRIGGER rescue_revocation_no_update BEFORE UPDATE ON rescue_revocation FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RESCUE_REVOCATION_IMMUTABLE'; END$$
CREATE TRIGGER rescue_revocation_no_delete BEFORE DELETE ON rescue_revocation FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RESCUE_REVOCATION_IMMUTABLE'; END$$
CREATE TRIGGER rescue_revocation_insert BEFORE INSERT ON rescue_revocation FOR EACH ROW
BEGIN
 IF EXISTS(SELECT 1 FROM rescue_grant WHERE id=NEW.grant_id AND issued_at>NEW.revoked_at) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RESCUE_REVOCATION_BEFORE_GRANT';
 END IF;
END$$
DELIMITER ;
