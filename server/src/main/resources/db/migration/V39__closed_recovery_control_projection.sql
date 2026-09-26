SET SESSION lock_wait_timeout=10;

-- No backfill: historical numeric epochs have no invented UUID or installation identity.
CREATE TABLE recovery_installation_binding (
    id TINYINT PRIMARY KEY,
    installation_id BINARY(16) NOT NULL UNIQUE,
    mysql_server_uuid BINARY(16) NOT NULL,
    database_schema VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    baseline_numeric_epoch BIGINT NOT NULL,
    initial_control_sha256 BINARY(32) NOT NULL,
    registered_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CHECK(id=1), CHECK(baseline_numeric_epoch>0)
) ENGINE=InnoDB;

CREATE TABLE recovery_control_epoch (
    installation_id BINARY(16) NOT NULL,
    epoch_id BINARY(16) NOT NULL,
    numeric_epoch BIGINT NOT NULL,
    first_control_revision BIGINT NOT NULL,
    first_control_sha256 BINARY(32) NOT NULL,
    PRIMARY KEY(installation_id,epoch_id),
    UNIQUE KEY uq_recovery_numeric_epoch(installation_id,numeric_epoch),
    UNIQUE KEY uq_recovery_epoch_identity(installation_id,epoch_id,numeric_epoch),
    FOREIGN KEY(installation_id) REFERENCES recovery_installation_binding(installation_id),
    CHECK(numeric_epoch>0), CHECK(first_control_revision>0)
) ENGINE=InnoDB;

CREATE TABLE recovery_control_projection (
    installation_id BINARY(16) NOT NULL,
    control_revision BIGINT NOT NULL,
    record_sha256 BINARY(32) NOT NULL,
    previous_record_sha256 BINARY(32) NOT NULL,
    epoch_id BINARY(16) NOT NULL,
    numeric_epoch BIGINT NOT NULL,
    control_phase VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    projected_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY(installation_id,control_revision),
    UNIQUE KEY uq_recovery_control_hash(installation_id,record_sha256),
    FOREIGN KEY(installation_id,epoch_id,numeric_epoch)
        REFERENCES recovery_control_epoch(installation_id,epoch_id,numeric_epoch),
    CHECK(control_revision>0),
    CHECK(control_phase IN ('QUIESCING','RECOVERING','VERIFYING','RELEASE_READY'))
) ENGINE=InnoDB;

DELIMITER $$
CREATE TRIGGER recovery_binding_no_update BEFORE UPDATE ON recovery_installation_binding FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovery installation binding is immutable'; END$$
CREATE TRIGGER recovery_binding_no_delete BEFORE DELETE ON recovery_installation_binding FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovery installation binding is retained'; END$$
CREATE TRIGGER recovery_epoch_no_update BEFORE UPDATE ON recovery_control_epoch FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovery epoch mapping is immutable'; END$$
CREATE TRIGGER recovery_epoch_no_delete BEFORE DELETE ON recovery_control_epoch FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovery epoch mapping is retained'; END$$
CREATE TRIGGER recovery_projection_no_update BEFORE UPDATE ON recovery_control_projection FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovery control projection is immutable'; END$$
CREATE TRIGGER recovery_projection_no_delete BEFORE DELETE ON recovery_control_projection FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovery control projection is retained'; END$$
DELIMITER ;
