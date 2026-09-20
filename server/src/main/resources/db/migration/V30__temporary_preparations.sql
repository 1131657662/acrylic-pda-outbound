SET SESSION lock_wait_timeout = 10;
CREATE TABLE temporary_authorization (
    id BINARY(16) PRIMARY KEY, bundle_id BINARY(16) NOT NULL UNIQUE,
    warehouse_id BINARY(16) NOT NULL, device_id BINARY(16) NOT NULL, user_id BINARY(16) NOT NULL,
    authorization_epoch BIGINT NOT NULL, user_auth_version BIGINT NOT NULL, device_version BIGINT NOT NULL,
    recovery_epoch BIGINT NOT NULL, inventory_snapshot_id BINARY(16) NOT NULL,
    mapping_revision BIGINT NOT NULL, policy_id BINARY(16) NOT NULL,
    issued_at DATETIME(3) NOT NULL, expires_at DATETIME(3) NOT NULL,
    key_id VARCHAR(80) CHARACTER SET ascii NOT NULL, signed_token TEXT CHARACTER SET ascii NOT NULL,
    manifest_text MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    manifest_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    signed_manifest TEXT CHARACTER SET ascii NOT NULL,
    UNIQUE KEY uq_temp_authorization_epoch(device_id,user_id,authorization_epoch),
    FOREIGN KEY(warehouse_id,device_id) REFERENCES device(warehouse_id,id),
    FOREIGN KEY(user_id) REFERENCES app_user(id),
    FOREIGN KEY(warehouse_id,inventory_snapshot_id) REFERENCES inventory_snapshot(warehouse_id,id),
    FOREIGN KEY(warehouse_id,policy_id) REFERENCES policy_version(warehouse_id,id),
    CHECK(authorization_epoch>0), CHECK(user_auth_version>0), CHECK(device_version>0),
    CHECK(recovery_epoch>0), CHECK(mapping_revision>=0),
    CHECK(TIMESTAMPDIFF(SECOND,issued_at,expires_at)=172800)
) ENGINE=InnoDB;
CREATE TABLE temporary_preparation_part (
    authorization_id BINARY(16) NOT NULL, part_name VARCHAR(20) CHARACTER SET ascii NOT NULL,
    compressed MEDIUMBLOB NOT NULL,
    PRIMARY KEY(authorization_id,part_name),
    FOREIGN KEY(authorization_id) REFERENCES temporary_authorization(id),
    CHECK(part_name IN ('inventory','mapping','policy','rules','availability'))
) ENGINE=InnoDB;
CREATE TABLE temporary_preparation_ack (
    authorization_id BINARY(16) PRIMARY KEY,
    command_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    activated_at DATETIME(3) NOT NULL, acknowledged_at DATETIME(3) NOT NULL,
    signed_ack TEXT CHARACTER SET ascii NOT NULL,
    FOREIGN KEY(authorization_id) REFERENCES temporary_authorization(id)
) ENGINE=InnoDB;
