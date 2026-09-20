SET SESSION lock_wait_timeout=10;

CREATE TABLE pda_audit_event (
    id BINARY(16) PRIMARY KEY, origin_device_id BINARY(16) NOT NULL, local_seq BIGINT NOT NULL,
    warehouse_id BINARY(16) NOT NULL, assignment_id BINARY(16) NOT NULL, operator_id BINARY(16) NOT NULL,
    source_recovery_epoch BIGINT NOT NULL, record_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload JSON NOT NULL, uploaded_by BINARY(16) NOT NULL, received_at DATETIME(3) NOT NULL,
    UNIQUE KEY uq_pda_audit_origin_seq(origin_device_id,local_seq),
    FOREIGN KEY(origin_device_id) REFERENCES device(id), FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),
    FOREIGN KEY(assignment_id) REFERENCES order_assignment(id), FOREIGN KEY(operator_id) REFERENCES app_user(id),
    FOREIGN KEY(uploaded_by) REFERENCES app_user(id), CHECK(local_seq>0), CHECK(source_recovery_epoch>0)
) ENGINE=InnoDB;

CREATE TABLE pda_audit_checkpoint (
    recovery_epoch BIGINT NOT NULL, origin_device_id BINARY(16) NOT NULL, through_seq BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY(recovery_epoch,origin_device_id), FOREIGN KEY(origin_device_id) REFERENCES device(id),
    CHECK(recovery_epoch>0), CHECK(through_seq>=0)
) ENGINE=InnoDB;
