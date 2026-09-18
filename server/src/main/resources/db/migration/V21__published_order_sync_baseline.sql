SET SESSION lock_wait_timeout=10;

CREATE UNIQUE INDEX uq_order_published_seq
    ON order_sync_outbox(recovery_epoch,warehouse_id,published_seq);

CREATE TABLE sync_order_projection (
    recovery_epoch BIGINT NOT NULL,
    warehouse_id BINARY(16) NOT NULL,
    order_id BINARY(16) NOT NULL,
    status VARCHAR(30) NOT NULL,
    row_version BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY(recovery_epoch,warehouse_id,order_id),
    FOREIGN KEY(warehouse_id,order_id) REFERENCES outbound_order(warehouse_id,id),
    CHECK(recovery_epoch>0), CHECK(row_version>0)
) ENGINE=InnoDB;

-- Replay only already published facts, never the current mutable business table.
INSERT INTO sync_order_projection
SELECT e.recovery_epoch,e.warehouse_id,e.order_id,
    CASE e.event_type WHEN 'ORDER_PUBLISHED' THEN 'READY'
        WHEN 'ORDER_ASSIGNED' THEN 'ASSIGNED' WHEN 'ORDER_CANCELLED' THEN 'CANCELLED'
        ELSE JSON_UNQUOTE(JSON_EXTRACT(e.payload,'$.status')) END,
    e.entity_version,e.created_at
FROM order_sync_outbox e
WHERE e.published_seq IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM order_sync_outbox newer
    WHERE newer.recovery_epoch=e.recovery_epoch AND newer.order_id=e.order_id
      AND newer.published_seq IS NOT NULL AND newer.entity_version>e.entity_version
);

CREATE TABLE sync_baseline (
    id BINARY(16) PRIMARY KEY,
    recovery_epoch BIGINT NOT NULL,
    warehouse_id BINARY(16) NOT NULL,
    stream_type VARCHAR(24) NOT NULL,
    high_watermark BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),
    KEY ix_sync_baseline_expiry(expires_at,id),
    CHECK(recovery_epoch>0), CHECK(stream_type='ORDER'), CHECK(high_watermark>=0), CHECK(expires_at>created_at)
) ENGINE=InnoDB;

CREATE TABLE sync_baseline_order (
    baseline_id BINARY(16) NOT NULL,
    order_id BINARY(16) NOT NULL,
    status VARCHAR(30) NOT NULL,
    row_version BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY(baseline_id,order_id),
    FOREIGN KEY(baseline_id) REFERENCES sync_baseline(id),
    FOREIGN KEY(order_id) REFERENCES outbound_order(id),
    CHECK(row_version>0)
) ENGINE=InnoDB;
