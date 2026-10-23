SET SESSION lock_wait_timeout=10;

-- No backfill. The original installation binding and first NORMAL remain immutable originals.
CREATE TABLE recovery_successor_binding (
    installation_id BINARY(16) NOT NULL,
    start_control_revision BIGINT NOT NULL,
    start_control_sha256 BINARY(32) NOT NULL,
    predecessor_release_sha256 BINARY(32) NOT NULL,
    predecessor_intent_sha256 BINARY(32) NOT NULL,
    source_binding_sha256 BINARY(32) NOT NULL,
    source_normal_sha256 BINARY(32) NOT NULL,
    mysql_server_uuid BINARY(16) NOT NULL,
    database_schema VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    binding_sha256 BINARY(32) NOT NULL,
    PRIMARY KEY(installation_id,start_control_revision),
    UNIQUE KEY uq_successor_binding_hash(installation_id,binding_sha256),
    FOREIGN KEY(installation_id,start_control_revision)
        REFERENCES recovery_control_projection(installation_id,control_revision),
    CHECK(start_control_revision>1)
) ENGINE=InnoDB;

CREATE TABLE recovery_successor_normal (
    installation_id BINARY(16) NOT NULL,
    binding_revision BIGINT NOT NULL,
    control_revision BIGINT NOT NULL,
    control_record_sha256 BINARY(32) NOT NULL,
    previous_record_sha256 BINARY(32) NOT NULL,
    intent_sha256 BINARY(32) NOT NULL,
    numeric_epoch BIGINT NOT NULL,
    operator_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    normal_sha256 BINARY(32) NOT NULL,
    PRIMARY KEY(installation_id,control_revision),
    UNIQUE KEY uq_successor_normal_round(installation_id,binding_revision),
    UNIQUE KEY uq_successor_normal_intent(installation_id,intent_sha256),
    FOREIGN KEY(installation_id,binding_revision)
        REFERENCES recovery_successor_binding(installation_id,start_control_revision),
    FOREIGN KEY(installation_id,control_revision)
        REFERENCES recovery_control_projection(installation_id,control_revision),
    CHECK(control_revision>binding_revision), CHECK(numeric_epoch>0),
    CHECK(CHAR_LENGTH(operator_id)>0)
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER successor_binding_no_update BEFORE UPDATE ON recovery_successor_binding FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Successor database binding is immutable'; END$$
CREATE TRIGGER successor_binding_no_delete BEFORE DELETE ON recovery_successor_binding FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Successor database binding is retained'; END$$
CREATE TRIGGER successor_normal_no_update BEFORE UPDATE ON recovery_successor_normal FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Successor NORMAL is immutable'; END$$
CREATE TRIGGER successor_normal_no_delete BEFORE DELETE ON recovery_successor_normal FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Successor NORMAL is retained'; END$$
DELIMITER ;
