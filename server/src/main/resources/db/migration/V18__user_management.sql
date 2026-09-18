SET SESSION lock_wait_timeout = 10;
CREATE TABLE user_management_guard (
    id TINYINT PRIMARY KEY, CHECK(id=1)
) ENGINE=InnoDB;
INSERT INTO user_management_guard VALUES(1);
CREATE TABLE user_command_receipt (
    id BINARY(16) PRIMARY KEY, user_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL, warehouse_id BINARY(16) NOT NULL,
    row_version BIGINT NOT NULL, status VARCHAR(20) NOT NULL, action VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL, password_verifier VARCHAR(100) NULL,
    created_at DATETIME(3) NOT NULL,
    FOREIGN KEY(user_id) REFERENCES app_user(id), FOREIGN KEY(actor_id) REFERENCES app_user(id),
    FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),
    CHECK(row_version>0), CHECK(status IN ('ACTIVE','DISABLED')),
    CHECK(action IN ('CREATE','UPDATE','PASSWORD'))
) ENGINE=InnoDB;
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000042'),'user:manage','管理人员角色及仓库授权');
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p
    WHERE r.code='WAREHOUSE_ADMIN' AND p.code='user:manage';
