SET SESSION lock_wait_timeout=10;

CREATE TABLE submission_review_reminder (
    id BINARY(16) PRIMARY KEY,
    submission_id BINARY(16) NOT NULL UNIQUE,
    warehouse_id BINARY(16) NOT NULL,
    order_id BINARY(16) NOT NULL,
    due_at DATETIME(3) NOT NULL,
    reminded_at DATETIME(3) NOT NULL,
    FOREIGN KEY(submission_id) REFERENCES submission(id),
    FOREIGN KEY(warehouse_id,order_id) REFERENCES outbound_order(warehouse_id,id),
    KEY ix_review_reminder_warehouse(warehouse_id,reminded_at,id),
    CHECK(due_at<=reminded_at)
) ENGINE=InnoDB;
