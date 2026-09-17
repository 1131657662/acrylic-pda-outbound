SET SESSION lock_wait_timeout = 10;
CREATE TABLE policy_version (
    id BINARY(16) PRIMARY KEY, warehouse_id BINARY(16) NOT NULL, version BIGINT NOT NULL,
    auto_deduct_inventory BOOLEAN NOT NULL, schema_version INT NOT NULL,
    offline_hours INT NOT NULL, short_shipment_policy VARCHAR(40) NOT NULL,
    concurrency_mode VARCHAR(40) NOT NULL, scan_mode VARCHAR(40) NOT NULL,
    reason VARCHAR(500) NOT NULL, published_by BINARY(16) NOT NULL, published_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_policy_version(warehouse_id,version), UNIQUE KEY uq_policy_warehouse(warehouse_id,id),
    FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), FOREIGN KEY(published_by) REFERENCES app_user(id),
    CHECK(version>0), CHECK(auto_deduct_inventory IN (0,1)), CHECK(schema_version=1), CHECK(offline_hours=48),
    CHECK(short_shipment_policy='SUPERVISOR_APPROVAL'), CHECK(concurrency_mode='PHYSICAL_SEPARATION'),
    CHECK(scan_mode='AUTO_SCAN_MANUAL_CONFIRM')
) ENGINE=InnoDB;
CREATE TABLE order_publication (
    id BINARY(16) PRIMARY KEY, warehouse_id BINARY(16) NOT NULL, order_id BINARY(16) NOT NULL,
    revision INT NOT NULL, row_version BIGINT NOT NULL, policy_id BINARY(16) NOT NULL,
    auto_deduct_inventory BOOLEAN NOT NULL, recovery_epoch BIGINT NOT NULL,
    snapshot_json JSON NOT NULL, reason VARCHAR(500) NOT NULL,
    published_by BINARY(16) NOT NULL, published_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_order_publication(order_id,revision), UNIQUE KEY uq_order_publication_version(order_id,row_version),
    FOREIGN KEY(warehouse_id,order_id) REFERENCES outbound_order(warehouse_id,id),
    FOREIGN KEY(order_id,revision) REFERENCES order_revision(order_id,revision),
    FOREIGN KEY(warehouse_id,policy_id) REFERENCES policy_version(warehouse_id,id),
    FOREIGN KEY(published_by) REFERENCES app_user(id),
    CHECK(row_version>0), CHECK(recovery_epoch>0), CHECK(auto_deduct_inventory IN (0,1))
) ENGINE=InnoDB;
CREATE TABLE order_plan_document_task (
    id BINARY(16) PRIMARY KEY, publication_id BINARY(16) NOT NULL UNIQUE,
    template_version VARCHAR(40) NOT NULL, status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
    KEY ix_plan_task_queue(status,created_at,id),
    FOREIGN KEY(publication_id) REFERENCES order_publication(id),
    CHECK(status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','SUPERSEDED'))
) ENGINE=InnoDB;
CREATE TABLE order_sync_outbox (
    id BINARY(16) PRIMARY KEY, recovery_epoch BIGINT NOT NULL, warehouse_id BINARY(16) NOT NULL,
    order_id BINARY(16) NOT NULL, entity_version BIGINT NOT NULL, event_type VARCHAR(40) NOT NULL,
    payload JSON NOT NULL, published_seq BIGINT NULL, created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_order_outbox_version(recovery_epoch,order_id,entity_version),
    KEY ix_order_outbox_pending(recovery_epoch,warehouse_id,published_seq,id),
    FOREIGN KEY(warehouse_id,order_id) REFERENCES outbound_order(warehouse_id,id),
    CHECK(recovery_epoch>0), CHECK(entity_version>0), CHECK(published_seq IS NULL OR published_seq>0)
) ENGINE=InnoDB;
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000024'),'order:publish','发布及修订未领取订单');
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000025'),'policy:manage','维护仓库出库政策');
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p
    WHERE r.code IN ('WAREHOUSE_ADMIN','ORDER_CLERK') AND p.code='order:publish';
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p
    WHERE r.code='WAREHOUSE_ADMIN' AND p.code='policy:manage';
