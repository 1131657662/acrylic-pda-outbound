ALTER TABLE warehouse ADD mapping_revision BIGINT NOT NULL DEFAULT 0, ADD CHECK(mapping_revision>=0);
CREATE TABLE product (
    id BINARY(16) PRIMARY KEY, model VARCHAR(100) COLLATE utf8mb4_0900_as_cs NOT NULL,
    name VARCHAR(100) COLLATE utf8mb4_0900_as_cs NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    UNIQUE KEY uq_product_description(model,name)
) ENGINE=InnoDB;
CREATE TABLE delivery_mapping (
    id BINARY(16) PRIMARY KEY, warehouse_id BINARY(16) NOT NULL,
    delivery_code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version BIGINT NOT NULL, product_id BINARY(16) NOT NULL,
    is_active BOOLEAN NOT NULL, enabled BOOLEAN NOT NULL,
    active_marker TINYINT GENERATED ALWAYS AS (IF(is_active,1,NULL)) STORED,
    source_import_id BINARY(16) NULL, created_by BINARY(16) NOT NULL,
    created_at DATETIME(3) NOT NULL, reason VARCHAR(500) NOT NULL,
    UNIQUE KEY uq_mapping_version(warehouse_id,delivery_code,version),
    UNIQUE KEY uq_mapping_current(warehouse_id,delivery_code,active_marker),
    FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), FOREIGN KEY(product_id) REFERENCES product(id),
    FOREIGN KEY(source_import_id) REFERENCES inventory_import(id), FOREIGN KEY(created_by) REFERENCES app_user(id),
    CHECK(version>0), CHECK(is_active IN (0,1)), CHECK(enabled IN (0,1))
) ENGINE=InnoDB;
CREATE TABLE inventory_snapshot (
    id BINARY(16) PRIMARY KEY, warehouse_id BINARY(16) NOT NULL, version BIGINT NOT NULL,
    import_id BINARY(16) NOT NULL UNIQUE, status VARCHAR(20) NOT NULL,
    row_count INT NOT NULL, total_weight_g BIGINT NOT NULL, mapping_revision BIGINT NOT NULL,
    manifest_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    published_by BINARY(16) NOT NULL, published_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_snapshot_version(warehouse_id,version), UNIQUE KEY uq_snapshot_warehouse(warehouse_id,id),
    FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), FOREIGN KEY(import_id) REFERENCES inventory_import(id),
    FOREIGN KEY(published_by) REFERENCES app_user(id),
    CHECK(version>0), CHECK(row_count>0), CHECK(total_weight_g>0),
    CHECK(status IN ('ACTIVE','SUPERSEDED','INVALID'))
) ENGINE=InnoDB;
ALTER TABLE warehouse ADD CONSTRAINT fk_active_inventory
    FOREIGN KEY(id,active_inventory_snapshot_id) REFERENCES inventory_snapshot(warehouse_id,id);
ALTER TABLE inventory_import ADD published_snapshot_id BINARY(16) NULL,
    ADD FOREIGN KEY(published_snapshot_id) REFERENCES inventory_snapshot(id);
CREATE TABLE bale (
    id BINARY(16) PRIMARY KEY, barcode CHAR(11) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    warehouse_id BINARY(16) NOT NULL, dispatch_cycle BIGINT NOT NULL DEFAULT 1, created_at DATETIME(3) NOT NULL,
    KEY ix_bale_warehouse(warehouse_id,barcode), FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), CHECK(dispatch_cycle>0)
) ENGINE=InnoDB;
CREATE TABLE inventory_snapshot_bale (
    snapshot_id BINARY(16) NOT NULL, bale_id BINARY(16) NOT NULL,
    delivery_code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    product_id BINARY(16) NOT NULL, model_snapshot VARCHAR(100) NOT NULL, name_snapshot VARCHAR(100) NOT NULL,
    weight_g BIGINT NOT NULL, raw_weight VARCHAR(20) NOT NULL,
    PRIMARY KEY(snapshot_id,bale_id), KEY ix_snapshot_code(snapshot_id,delivery_code,bale_id),
    FOREIGN KEY(snapshot_id) REFERENCES inventory_snapshot(id), FOREIGN KEY(bale_id) REFERENCES bale(id),
    FOREIGN KEY(product_id) REFERENCES product(id), CHECK(weight_g>0)
) ENGINE=InnoDB;
CREATE TABLE inventory_position (
    bale_id BINARY(16) PRIMARY KEY, latest_snapshot_id BINARY(16) NOT NULL,
    last_seen_snapshot_id BINARY(16) NOT NULL, upstream_present BOOLEAN NOT NULL,
    book_status VARCHAR(30) NOT NULL DEFAULT 'NOT_DEDUCTED', blocked_reason VARCHAR(200) NULL,
    row_version BIGINT NOT NULL DEFAULT 1,
    KEY ix_position_book(book_status,bale_id),
    FOREIGN KEY(bale_id) REFERENCES bale(id), FOREIGN KEY(latest_snapshot_id) REFERENCES inventory_snapshot(id),
    FOREIGN KEY(last_seen_snapshot_id,bale_id) REFERENCES inventory_snapshot_bale(snapshot_id,bale_id),
    CHECK(upstream_present IN (0,1)), CHECK(row_version>0), CHECK(book_status IN ('NOT_DEDUCTED','DEDUCTED'))
) ENGINE=InnoDB;
CREATE TABLE inventory_discrepancy (
    id BINARY(16) PRIMARY KEY, warehouse_id BINARY(16) NOT NULL, bale_id BINARY(16) NOT NULL,
    old_snapshot_id BINARY(16) NULL, new_snapshot_id BINARY(16) NOT NULL,
    type VARCHAR(40) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    old_values JSON NULL, new_values JSON NULL, created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_discrepancy(new_snapshot_id,bale_id,type), KEY ix_discrepancy_list(warehouse_id,status,id),
    FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), FOREIGN KEY(bale_id) REFERENCES bale(id),
    FOREIGN KEY(old_snapshot_id) REFERENCES inventory_snapshot(id), FOREIGN KEY(new_snapshot_id) REFERENCES inventory_snapshot(id)
) ENGINE=InnoDB;
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000013'),'inventory:publish','发布库存版本');
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-000000000014'),'mapping:maintain','维护代码映射');
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p
    WHERE r.code='WAREHOUSE_ADMIN' AND p.code IN ('inventory:publish','mapping:maintain');
