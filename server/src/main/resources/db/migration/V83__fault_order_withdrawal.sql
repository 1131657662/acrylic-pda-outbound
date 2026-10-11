SET SESSION lock_wait_timeout=10;
ALTER TABLE order_change_request
 DROP CHECK order_change_request_chk_3,
 DROP CHECK order_change_request_chk_4,
 ADD CONSTRAINT ck_change_request_status CHECK(status IN ('REQUESTED','STOPPED','CANCELLED','WITHDRAWN')),
 ADD CONSTRAINT ck_change_request_stop CHECK(status IN ('REQUESTED','WITHDRAWN') OR stop_receipt IS NOT NULL),
 ADD CONSTRAINT ck_fault_withdrawal_resolution CHECK(status!='WITHDRAWN' OR
   (resolution_receipt IS NOT NULL AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL));
