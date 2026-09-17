CREATE TABLE inventory_import (
    id BINARY(16) NOT NULL PRIMARY KEY,
    warehouse_id BINARY(16) NOT NULL,
    original_filename VARCHAR(200) NOT NULL,
    file_key VARCHAR(200) NOT NULL,
    file_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    parser_version VARCHAR(50) NOT NULL,
    status VARCHAR(40) NOT NULL,
    total_rows INT NOT NULL, valid_rows INT NOT NULL, error_rows INT NOT NULL,
    duplicate_rows INT NOT NULL, ignored_rows INT NOT NULL, product_count INT NOT NULL,
    total_weight_g BIGINT NOT NULL,
    created_by BINARY(16) NOT NULL, created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_import_file (warehouse_id,file_sha256,parser_version),
    KEY ix_import_history (warehouse_id,created_at,id),
    FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),
    FOREIGN KEY(created_by) REFERENCES app_user(id),
    CHECK (status IN ('READY_FOR_REVIEW','BLOCKED','NEEDS_DEDUP_CONFIRMATION')),
    CHECK (total_rows>=0 AND valid_rows>=0 AND error_rows>=0 AND duplicate_rows>=0),
    CHECK (total_weight_g>=0)
) ENGINE=InnoDB;
CREATE TABLE inventory_import_row (
    import_id BINARY(16) NOT NULL, line_no INT NOT NULL,
    raw_text TEXT NOT NULL, parsed_fields JSON NULL,
    error_code VARCHAR(60) NULL, error_detail VARCHAR(500) NULL,
    PRIMARY KEY(import_id,line_no),
    KEY ix_import_errors (import_id,error_code,line_no),
    FOREIGN KEY(import_id) REFERENCES inventory_import(id), CHECK(line_no>0)
) ENGINE=InnoDB;
CREATE TABLE api_idempotency_request (
    id BINARY(16) PRIMARY KEY, principal_id BINARY(16) NOT NULL,
    route_scope VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resource_id BINARY(16) NOT NULL, created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_api_request (principal_id,route_scope,idempotency_key),
    FOREIGN KEY(principal_id) REFERENCES app_user(id)
) ENGINE=InnoDB;
CREATE TABLE operation_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, actor_id BINARY(16) NULL,
    event_type VARCHAR(80) NOT NULL, entity_type VARCHAR(60) NOT NULL,
    entity_id BINARY(16) NULL, details JSON NOT NULL,
    server_time DATETIME(3) NOT NULL, request_id VARCHAR(64) NOT NULL,
    KEY ix_event_entity (entity_type,entity_id,server_time,id),
    KEY ix_event_actor (actor_id,server_time,id),
    FOREIGN KEY(actor_id) REFERENCES app_user(id)
) ENGINE=InnoDB;
