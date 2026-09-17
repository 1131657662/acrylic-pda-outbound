SET SESSION lock_wait_timeout=10;
CREATE TABLE device (
 id BINARY(16) PRIMARY KEY, warehouse_id BINARY(16) NOT NULL,
 device_code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 name VARCHAR(100) NOT NULL, public_key VARCHAR(200) CHARACTER SET ascii NOT NULL,
 key_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 status VARCHAR(20) NOT NULL, row_version BIGINT NOT NULL, created_by BINARY(16) NOT NULL,
 created_at DATETIME(3) NOT NULL, last_seen_at DATETIME(3) NULL,
 UNIQUE KEY uq_device_warehouse(warehouse_id,id), KEY ix_device_list(warehouse_id,id),
 FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), FOREIGN KEY(created_by) REFERENCES app_user(id),
 CHECK(status IN ('ACTIVE','DISABLED')), CHECK(row_version>0)
) ENGINE=InnoDB;
CREATE TABLE device_command_receipt (
 id BINARY(16) PRIMARY KEY, device_id BINARY(16) NOT NULL, row_version BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL, created_at DATETIME(3) NOT NULL,
 FOREIGN KEY(device_id) REFERENCES device(id)
) ENGINE=InnoDB;
CREATE TABLE device_challenge (
 id BINARY(16) PRIMARY KEY, device_id BINARY(16) NOT NULL, purpose VARCHAR(20) NOT NULL,
 nonce CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, expires_at DATETIME(3) NOT NULL,
 used_at DATETIME(3) NULL, created_at DATETIME(3) NOT NULL,
 FOREIGN KEY(device_id) REFERENCES device(id), KEY ix_challenge_expiry(expires_at),
 CHECK(purpose IN ('LOGIN','REFRESH'))
) ENGINE=InnoDB;
CREATE TABLE pda_session (
 id BINARY(16) PRIMARY KEY, device_id BINARY(16) NOT NULL, user_id BINARY(16) NOT NULL,
 user_auth_version BIGINT NOT NULL, device_version BIGINT NOT NULL,
 access_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 refresh_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 access_expires_at DATETIME(3) NOT NULL, expires_at DATETIME(3) NOT NULL,
 revoked_at DATETIME(3) NULL, created_at DATETIME(3) NOT NULL,
 FOREIGN KEY(device_id) REFERENCES device(id), FOREIGN KEY(user_id) REFERENCES app_user(id),
 KEY ix_pda_session_device(device_id,expires_at), KEY ix_pda_session_user(user_id,expires_at)
) ENGINE=InnoDB;
ALTER TABLE outbound_order ADD COLUMN assignment_epoch BIGINT NOT NULL DEFAULT 0;
CREATE TABLE order_assignment (
 id BINARY(16) PRIMARY KEY, warehouse_id BINARY(16) NOT NULL, order_id BINARY(16) NOT NULL,
 revision INT NOT NULL, publication_id BINARY(16) NOT NULL, device_id BINARY(16) NOT NULL,
 operator_id BINARY(16) NOT NULL, epoch BIGINT NOT NULL, recovery_epoch BIGINT NOT NULL,
 inventory_snapshot_id BINARY(16) NOT NULL, policy_id BINARY(16) NOT NULL,
 plan_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 inventory_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 start_order_scan_seq BIGINT NOT NULL, allocation_version BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL, area_note VARCHAR(500) NOT NULL,
 issued_at DATETIME(3) NOT NULL, expires_at DATETIME(3) NOT NULL, download_ack_at DATETIME(3) NULL,
 active_marker TINYINT GENERATED ALWAYS AS (CASE WHEN status IN ('ACTIVE','RECALL_PENDING') THEN 1 ELSE NULL END) STORED,
 UNIQUE KEY uq_assignment_active(order_id,active_marker), UNIQUE KEY uq_assignment_epoch(order_id,epoch),
 KEY ix_assignment_device(device_id,status,id),
 FOREIGN KEY(warehouse_id,order_id) REFERENCES outbound_order(warehouse_id,id),
 FOREIGN KEY(order_id,revision) REFERENCES order_revision(order_id,revision),
 FOREIGN KEY(publication_id) REFERENCES order_publication(id), FOREIGN KEY(warehouse_id,device_id) REFERENCES device(warehouse_id,id),
 FOREIGN KEY(operator_id) REFERENCES app_user(id),
 FOREIGN KEY(warehouse_id,inventory_snapshot_id) REFERENCES inventory_snapshot(warehouse_id,id),
 FOREIGN KEY(warehouse_id,policy_id) REFERENCES policy_version(warehouse_id,id),
 CHECK(epoch>0), CHECK(recovery_epoch>0), CHECK(start_order_scan_seq>0), CHECK(allocation_version>=0),
 CHECK(expires_at>issued_at), CHECK(status IN ('ACTIVE','RECALL_PENDING','CLOSED','REVOKED'))
) ENGINE=InnoDB;
CREATE TABLE offline_authorization (
 id BINARY(16) PRIMARY KEY, assignment_id BINARY(16) NOT NULL UNIQUE,
 user_id BINARY(16) NOT NULL, device_id BINARY(16) NOT NULL,
 issued_at DATETIME(3) NOT NULL, expires_at DATETIME(3) NOT NULL, revoked_at DATETIME(3) NULL,
 key_id VARCHAR(80) CHARACTER SET ascii NOT NULL, signed_token TEXT CHARACTER SET ascii NOT NULL,
 FOREIGN KEY(assignment_id) REFERENCES order_assignment(id), FOREIGN KEY(user_id) REFERENCES app_user(id),
 FOREIGN KEY(device_id) REFERENCES device(id), KEY ix_offline_device(device_id,expires_at)
) ENGINE=InnoDB;
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000031'),'device:manage','登记及启停PDA设备');
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000032'),'pda:login','登录已绑定的PDA');
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000033'),'pda:assign','领取正式订单');
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000034'),'pda:scan','离线扫码作业');
INSERT INTO role VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000005'),'PDA_OPERATOR','PDA操作员');
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p
 WHERE r.code='WAREHOUSE_ADMIN' AND p.code IN ('device:manage','pda:login','pda:assign','pda:scan');
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p
 WHERE r.code='PDA_OPERATOR' AND p.code IN ('order:read','pda:login','pda:assign','pda:scan');
