SET SESSION lock_wait_timeout=10;
-- Repeated operations use a new immutable plan revision, not a reused scan prefix.
ALTER TABLE submitted_capture ADD COLUMN operation_revision INT NOT NULL DEFAULT 1;
UPDATE submitted_capture c INNER JOIN submission s ON s.id=c.submission_id
 INNER JOIN order_assignment a ON a.id=s.assignment_id SET c.operation_revision=a.revision;
ALTER TABLE submitted_capture
 DROP INDEX uq_capture_order,
 ADD UNIQUE KEY uq_capture_operation(order_id,operation_revision,order_scan_seq),
 ADD CONSTRAINT fk_capture_operation FOREIGN KEY(order_id,operation_revision) REFERENCES order_revision(order_id,revision);

CREATE TABLE fault_withdrawal_stock (
 request_id BINARY(16) NOT NULL, bale_id BINARY(16) NOT NULL, submission_id BINARY(16) NOT NULL,
 dispatch_cycle BIGINT NOT NULL, original_state VARCHAR(30) NOT NULL,
 physical_evidence VARCHAR(1000) NOT NULL, verified_by BINARY(16) NOT NULL,
 released_at DATETIME(3) NOT NULL,
 PRIMARY KEY(request_id,bale_id),
 FOREIGN KEY(request_id) REFERENCES order_change_request(id),
 FOREIGN KEY(bale_id) REFERENCES bale(id), FOREIGN KEY(submission_id) REFERENCES submission(id),
 FOREIGN KEY(verified_by) REFERENCES app_user(id),
 CHECK(dispatch_cycle>0), CHECK(original_state='REVIEW_RESERVED'),
 CHECK(CHAR_LENGTH(TRIM(physical_evidence))>0)
) ENGINE=InnoDB;
