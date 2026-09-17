SET SESSION lock_wait_timeout = 10;
CREATE TABLE outbound_order (
    id BINARY(16) PRIMARY KEY, warehouse_id BINARY(16) NOT NULL,
    order_no VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    source VARCHAR(20) NOT NULL, status VARCHAR(30) NOT NULL,
    current_revision INT NULL, row_version BIGINT NOT NULL DEFAULT 1,
    created_by BINARY(16) NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
    KEY ix_order_list(warehouse_id,created_at DESC,id DESC),
    KEY ix_order_status(warehouse_id,status,created_at DESC,id DESC),
    FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), FOREIGN KEY(created_by) REFERENCES app_user(id),
    UNIQUE KEY uq_order_warehouse(warehouse_id,id),
    CHECK(row_version>0), CHECK(current_revision IS NULL OR current_revision>0),
    CHECK(source IN ('WEB','PDA_TEMP')),
    CHECK(status IN ('DRAFT','READY','ASSIGNED','RECALL_PENDING','REVIEW_REQUIRED','COMPLETED','CANCELLED','CONTINUING','LINKED'))
) ENGINE=InnoDB;
CREATE TABLE order_revision (
    id BINARY(16) PRIMARY KEY, order_id BINARY(16) NOT NULL, warehouse_id BINARY(16) NOT NULL,
    revision INT NOT NULL, inventory_snapshot_id BINARY(16) NOT NULL, mapping_revision BIGINT NOT NULL,
    vehicle_no VARCHAR(40) NOT NULL, container_no VARCHAR(40) NOT NULL, notes VARCHAR(1000) NOT NULL,
    planned_total BIGINT NOT NULL, reason VARCHAR(500) NOT NULL,
    created_by BINARY(16) NOT NULL, created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_order_revision(order_id,revision),
    FOREIGN KEY(warehouse_id,order_id) REFERENCES outbound_order(warehouse_id,id),
    FOREIGN KEY(warehouse_id,inventory_snapshot_id) REFERENCES inventory_snapshot(warehouse_id,id),
    FOREIGN KEY(created_by) REFERENCES app_user(id),
    CHECK(revision>0), CHECK(mapping_revision>=0), CHECK(planned_total>0)
) ENGINE=InnoDB;
ALTER TABLE outbound_order ADD CONSTRAINT fk_order_current_revision
    FOREIGN KEY(id,current_revision) REFERENCES order_revision(order_id,revision);
CREATE TABLE order_line (
    revision_id BINARY(16) NOT NULL, delivery_code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    mapping_id BINARY(16) NOT NULL, product_id BINARY(16) NOT NULL,
    model_snapshot VARCHAR(100) NOT NULL, name_snapshot VARCHAR(100) NOT NULL,
    planned_count INT NOT NULL,
    PRIMARY KEY(revision_id,delivery_code),
    FOREIGN KEY(revision_id) REFERENCES order_revision(id), FOREIGN KEY(mapping_id) REFERENCES delivery_mapping(id),
    FOREIGN KEY(product_id) REFERENCES product(id), CHECK(planned_count>0)
) ENGINE=InnoDB;
CREATE TABLE order_command_receipt (
    id BINARY(16) PRIMARY KEY, order_id BINARY(16) NOT NULL, revision INT NOT NULL,
    row_version BIGINT NOT NULL, status VARCHAR(30) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    FOREIGN KEY(order_id,revision) REFERENCES order_revision(order_id,revision), CHECK(row_version>0)
) ENGINE=InnoDB;
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000021'),'order:read','查看订单及计划版本');
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000022'),'order:write','创建及修订未作业订单');
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000023'),'order:cancel','作废未作业订单');
INSERT INTO role VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000003'),'ORDER_CLERK','订单文员');
INSERT INTO role VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000004'),'ORDER_VIEWER','订单查看员');
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p
    WHERE r.code IN ('WAREHOUSE_ADMIN','ORDER_CLERK') AND p.code IN ('order:read','order:write','order:cancel');
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p
    WHERE r.code='ORDER_VIEWER' AND p.code='order:read';
