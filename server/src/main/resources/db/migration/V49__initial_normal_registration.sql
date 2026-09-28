SET SESSION lock_wait_timeout=10;

-- Independent initial baseline. No backfill, no closed recovery phase, no server_state mutation.
CREATE TABLE recovery_initial_normal (
    id TINYINT PRIMARY KEY,
    format_version TINYINT NOT NULL,
    installation_id BINARY(16) NOT NULL UNIQUE,
    epoch_id BINARY(16) NOT NULL,
    numeric_epoch BIGINT NOT NULL,
    mysql_server_uuid BINARY(16) NOT NULL,
    database_schema VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    command_id BINARY(16) NOT NULL UNIQUE,
    request_sha256 BINARY(32) NOT NULL,
    request_bytes MEDIUMBLOB NOT NULL,
    registered_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CHECK(id=1), CHECK(format_version=1), CHECK(numeric_epoch>0),
    CHECK(OCTET_LENGTH(request_bytes) BETWEEN 1 AND 65536),
    CHECK(CHAR_LENGTH(database_schema)>0)
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER recovery_initial_no_update BEFORE UPDATE ON recovery_initial_normal FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Initial normal registration is immutable'; END$$
CREATE TRIGGER recovery_initial_no_delete BEFORE DELETE ON recovery_initial_normal FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Initial normal registration is retained'; END$$
DELIMITER ;
