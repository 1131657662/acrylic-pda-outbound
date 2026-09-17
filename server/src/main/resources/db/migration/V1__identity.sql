CREATE TABLE warehouse (
    id BINARY(16) PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    active_inventory_snapshot_id BINARY(16) NULL,
    CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB;
CREATE TABLE app_user (
    id BINARY(16) PRIMARY KEY,
    username VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    auth_version BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL,
    CHECK (status IN ('ACTIVE','DISABLED')),
    CHECK (auth_version > 0)
) ENGINE=InnoDB;
CREATE TABLE role (
    id BINARY(16) PRIMARY KEY, code VARCHAR(60) NOT NULL UNIQUE, name VARCHAR(100) NOT NULL
) ENGINE=InnoDB;
CREATE TABLE permission (
    id BINARY(16) PRIMARY KEY, code VARCHAR(80) NOT NULL UNIQUE, name VARCHAR(100) NOT NULL
) ENGINE=InnoDB;
CREATE TABLE user_role (
    user_id BINARY(16) NOT NULL, role_id BINARY(16) NOT NULL,
    PRIMARY KEY(user_id,role_id),
    FOREIGN KEY(user_id) REFERENCES app_user(id), FOREIGN KEY(role_id) REFERENCES role(id)
) ENGINE=InnoDB;
CREATE TABLE role_permission (
    role_id BINARY(16) NOT NULL, permission_id BINARY(16) NOT NULL,
    PRIMARY KEY(role_id,permission_id),
    FOREIGN KEY(role_id) REFERENCES role(id), FOREIGN KEY(permission_id) REFERENCES permission(id)
) ENGINE=InnoDB;
CREATE TABLE user_warehouse (
    user_id BINARY(16) NOT NULL, warehouse_id BINARY(16) NOT NULL,
    PRIMARY KEY(user_id,warehouse_id),
    FOREIGN KEY(user_id) REFERENCES app_user(id), FOREIGN KEY(warehouse_id) REFERENCES warehouse(id)
) ENGINE=InnoDB;
CREATE TABLE server_state (
    id TINYINT PRIMARY KEY, recovery_epoch BIGINT NOT NULL, service_mode VARCHAR(30) NOT NULL,
    CHECK(id=1), CHECK(recovery_epoch>0), CHECK(service_mode IN ('NORMAL','RECOVERY_PENDING'))
) ENGINE=InnoDB;
INSERT INTO server_state VALUES (1,1,'NORMAL');
INSERT INTO role VALUES (UUID_TO_BIN('01994300-0000-7000-8000-000000000001'),'WAREHOUSE_ADMIN','库存管理员');
INSERT INTO role VALUES (UUID_TO_BIN('01994300-0000-7000-8000-000000000002'),'WAREHOUSE_VIEWER','库存查看员');
INSERT INTO permission VALUES (UUID_TO_BIN('01994300-0000-7000-8000-000000000011'),'inventory:read','查看库存导入');
INSERT INTO permission VALUES (UUID_TO_BIN('01994300-0000-7000-8000-000000000012'),'inventory:import','上传库存预检');
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p WHERE r.code='WAREHOUSE_ADMIN';
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p
    WHERE r.code='WAREHOUSE_VIEWER' AND p.code='inventory:read';
