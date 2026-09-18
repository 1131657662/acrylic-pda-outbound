SET SESSION lock_wait_timeout=10;

CREATE TABLE sync_stream (
    recovery_epoch BIGINT NOT NULL,
    warehouse_id BINARY(16) NOT NULL,
    stream_type VARCHAR(24) NOT NULL,
    next_seq BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY(recovery_epoch,warehouse_id,stream_type),
    FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),
    CHECK(recovery_epoch>0), CHECK(next_seq>0), CHECK(stream_type='ORDER')
) ENGINE=InnoDB;

CREATE INDEX ix_order_outbox_stream
    ON order_sync_outbox(recovery_epoch,warehouse_id,published_seq,created_at,id);
