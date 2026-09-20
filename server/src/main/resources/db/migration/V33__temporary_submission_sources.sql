SET SESSION lock_wait_timeout=10;
ALTER TABLE submission_scope
    MODIFY source_order_id BINARY(16) NULL,
    ADD creator_device_id BINARY(16) NULL,
    ADD client_order_id BINARY(16) NULL,
    ADD canonical_order_id BINARY(16) NULL,
    ADD UNIQUE KEY uq_submission_temporary(creator_device_id,client_order_id),
    ADD FOREIGN KEY(creator_device_id,client_order_id) REFERENCES temporary_work_origin(creator_device_id,client_order_id),
    ADD FOREIGN KEY(warehouse_id,canonical_order_id) REFERENCES outbound_order(warehouse_id,id),
    ADD CONSTRAINT ck_submission_scope_source CHECK(
        (source_order_id IS NOT NULL AND creator_device_id IS NULL AND client_order_id IS NULL AND canonical_order_id IS NULL) OR
        (source_order_id IS NULL AND creator_device_id IS NOT NULL AND client_order_id IS NOT NULL));
ALTER TABLE submission
    MODIFY assignment_id BINARY(16) NULL,
    ADD temporary_authorization_id BINARY(16) NULL,
    ADD FOREIGN KEY(temporary_authorization_id) REFERENCES temporary_authorization(id),
    ADD CONSTRAINT ck_submission_authorization CHECK(
        (assignment_id IS NOT NULL AND temporary_authorization_id IS NULL) OR
        (assignment_id IS NULL AND temporary_authorization_id IS NOT NULL AND continuation_id IS NULL));
ALTER TABLE order_revision MODIFY vehicle_no VARCHAR(50) NOT NULL, MODIFY container_no VARCHAR(50) NOT NULL;
