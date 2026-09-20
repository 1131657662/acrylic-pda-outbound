SET SESSION lock_wait_timeout=10;

-- Business transactions mark each changed bale. Publishing coalesces committed state,
-- not business audit history; publishing never takes locks on business rows.
CREATE TABLE barcode_sync_pending (
    recovery_epoch BIGINT NOT NULL, warehouse_id BINARY(16) NOT NULL, bale_id BINARY(16) NOT NULL,
    row_version BIGINT NOT NULL, published_version BIGINT NOT NULL DEFAULT 0,
    pending BOOLEAN GENERATED ALWAYS AS (row_version>published_version) STORED,
    PRIMARY KEY(recovery_epoch,warehouse_id,bale_id),
    KEY ix_barcode_pending(recovery_epoch,warehouse_id,pending,bale_id),
    FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), FOREIGN KEY(bale_id) REFERENCES bale(id),
    CHECK(recovery_epoch>0), CHECK(row_version>0), CHECK(published_version BETWEEN 0 AND row_version)
) ENGINE=InnoDB;

CREATE TABLE barcode_sync_stream (
    recovery_epoch BIGINT NOT NULL, warehouse_id BINARY(16) NOT NULL, next_seq BIGINT NOT NULL DEFAULT 1, seeded BOOLEAN NOT NULL DEFAULT 0,
    PRIMARY KEY(recovery_epoch,warehouse_id), FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),
    CHECK(recovery_epoch>0), CHECK(next_seq>0), CHECK(seeded IN (0,1))
) ENGINE=InnoDB;

CREATE TABLE barcode_sync_event (
    recovery_epoch BIGINT NOT NULL, warehouse_id BINARY(16) NOT NULL, sequence_no BIGINT NOT NULL,
    id BINARY(16) NOT NULL UNIQUE, bale_id BINARY(16) NOT NULL,
    barcode CHAR(11) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(24) NOT NULL, dispatch_cycle BIGINT NOT NULL, row_version BIGINT NOT NULL, created_at DATETIME(3) NOT NULL,
    PRIMARY KEY(recovery_epoch,warehouse_id,sequence_no),
    FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), FOREIGN KEY(bale_id) REFERENCES bale(id),
    CHECK(sequence_no>0), CHECK(dispatch_cycle>0), CHECK(row_version>0),
    CHECK(status IN ('AVAILABLE','UNAVAILABLE','RESERVED','DISPATCHED','QUARANTINED'))
) ENGINE=InnoDB;

CREATE TABLE barcode_sync_projection (
    recovery_epoch BIGINT NOT NULL, warehouse_id BINARY(16) NOT NULL, bale_id BINARY(16) NOT NULL,
    barcode CHAR(11) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(24) NOT NULL, dispatch_cycle BIGINT NOT NULL, row_version BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY(recovery_epoch,warehouse_id,bale_id),
    FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), FOREIGN KEY(bale_id) REFERENCES bale(id),
    CHECK(dispatch_cycle>0), CHECK(row_version>0),
    CHECK(status IN ('AVAILABLE','UNAVAILABLE','RESERVED','DISPATCHED','QUARANTINED'))
) ENGINE=InnoDB;

CREATE TABLE barcode_sync_baseline (
    id BINARY(16) PRIMARY KEY, recovery_epoch BIGINT NOT NULL, warehouse_id BINARY(16) NOT NULL,
    high_watermark BIGINT NOT NULL, created_at DATETIME(3) NOT NULL, expires_at DATETIME(3) NOT NULL,
    FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), KEY ix_barcode_baseline_expiry(expires_at,id),
    CHECK(recovery_epoch>0), CHECK(high_watermark>=0), CHECK(expires_at>created_at)
) ENGINE=InnoDB;

CREATE TABLE barcode_sync_baseline_row (
    baseline_id BINARY(16) NOT NULL, bale_id BINARY(16) NOT NULL,
    barcode CHAR(11) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(24) NOT NULL, dispatch_cycle BIGINT NOT NULL, row_version BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY(baseline_id,bale_id), FOREIGN KEY(baseline_id) REFERENCES barcode_sync_baseline(id),
    FOREIGN KEY(bale_id) REFERENCES bale(id), CHECK(dispatch_cycle>0), CHECK(row_version>0)
) ENGINE=InnoDB;
