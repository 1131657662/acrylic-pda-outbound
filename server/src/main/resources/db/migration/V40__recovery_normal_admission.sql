SET SESSION lock_wait_timeout=10;

-- Initial NORMAL admission is its own record: it never reuses the post-disaster RELEASE_READY
-- release path and it is not populated by the migration. A row exists only after an explicit
-- registration that verified the durable external intent and the database projection agree.
CREATE TABLE recovery_normal_admission (
    id TINYINT PRIMARY KEY,
    format_version TINYINT NOT NULL,
    installation_id BINARY(16) NOT NULL UNIQUE,
    authority_revision BIGINT NOT NULL,
    control_revision BIGINT NOT NULL,
    control_record_sha256 BINARY(32) NOT NULL,
    previous_record_sha256 BINARY(32) NOT NULL,
    intent_sha256 BINARY(32) NOT NULL,
    numeric_epoch BIGINT NOT NULL,
    operator_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    registered_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY(installation_id) REFERENCES recovery_installation_binding(installation_id),
    CHECK(id=1), CHECK(format_version=1), CHECK(authority_revision>0), CHECK(control_revision>0), CHECK(numeric_epoch>0),
    CHECK(CHAR_LENGTH(operator_id)>0)
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER recovery_normal_no_update BEFORE UPDATE ON recovery_normal_admission FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovery normal admission is immutable'; END$$
CREATE TRIGGER recovery_normal_no_delete BEFORE DELETE ON recovery_normal_admission FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovery normal admission is retained'; END$$
DELIMITER ;
