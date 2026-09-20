SET SESSION lock_wait_timeout=10;

ALTER TABLE temporary_authorization ADD UNIQUE KEY uq_temp_authorization_device(device_id,id);
CREATE TABLE temporary_work_origin (
    creator_device_id BINARY(16) NOT NULL, client_order_id BINARY(16) NOT NULL,
    authorization_id BINARY(16) NOT NULL,
    PRIMARY KEY(creator_device_id,client_order_id),
    UNIQUE KEY uq_temp_work_authorization(creator_device_id,client_order_id,authorization_id),
    FOREIGN KEY(creator_device_id,authorization_id) REFERENCES temporary_authorization(device_id,id)
) ENGINE=InnoDB;

ALTER TABLE pda_audit_event
    MODIFY assignment_id BINARY(16) NULL,
    ADD temporary_authorization_id BINARY(16) NULL,
    ADD client_order_id BINARY(16) NULL,
    ADD CONSTRAINT fk_audit_temp_origin FOREIGN KEY(origin_device_id,client_order_id,temporary_authorization_id)
        REFERENCES temporary_work_origin(creator_device_id,client_order_id,authorization_id),
    ADD CONSTRAINT ck_audit_origin CHECK(
        (assignment_id IS NOT NULL AND temporary_authorization_id IS NULL AND client_order_id IS NULL) OR
        (assignment_id IS NULL AND temporary_authorization_id IS NOT NULL AND client_order_id IS NOT NULL));
