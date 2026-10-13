-- Additive, append-only signed evidence. MySQL UTC session is configured by the existing datasource.
SET SESSION lock_wait_timeout=10;
CREATE TABLE independent_capture_credential (
    id BINARY(16) NOT NULL PRIMARY KEY,
    operator_id BINARY(16) NOT NULL,
    device_id BINARY(16) NOT NULL,
    warehouse_id BINARY(16) NOT NULL,
    device_key_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_auth_version BIGINT NOT NULL,
    device_version BIGINT NOT NULL,
    recovery_epoch BIGINT NOT NULL,
    issued_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    signed_credential TEXT CHARACTER SET ascii NOT NULL,
    credential_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    CONSTRAINT fk_independent_credential_operator FOREIGN KEY (operator_id) REFERENCES app_user(id),
    CONSTRAINT fk_independent_credential_device FOREIGN KEY (device_id) REFERENCES device(id),
    CONSTRAINT fk_independent_credential_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(id),
    CONSTRAINT ck_independent_credential_window CHECK (expires_at > issued_at AND expires_at <= TIMESTAMPADD(HOUR,48,issued_at)),
    CONSTRAINT ck_independent_credential_versions CHECK (user_auth_version > 0 AND device_version > 0 AND recovery_epoch > 0),
    CONSTRAINT ck_independent_credential_bytes CHECK (OCTET_LENGTH(signed_credential) BETWEEN 1 AND 16384),
    CONSTRAINT ck_independent_credential_digests CHECK (device_key_sha256 REGEXP '^[0-9a-f]{64}$' AND credential_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB;

INSERT INTO permission(id,code,name) VALUES
    (UUID_TO_BIN('019a0000-0000-7000-8000-000000000104'),'pda:independent','预置受限独立采集身份');
INSERT INTO role_permission(role_id,permission_id)
    SELECT id,UUID_TO_BIN('019a0000-0000-7000-8000-000000000104') FROM role
    WHERE code IN ('WAREHOUSE_ADMIN','PDA_OPERATOR');

DELIMITER $$
CREATE TRIGGER independent_credential_no_update BEFORE UPDATE ON independent_capture_credential FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INDEPENDENT_CREDENTIAL_IMMUTABLE'; END$$
CREATE TRIGGER independent_credential_no_delete BEFORE DELETE ON independent_capture_credential FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INDEPENDENT_CREDENTIAL_IMMUTABLE'; END$$
DELIMITER ;
