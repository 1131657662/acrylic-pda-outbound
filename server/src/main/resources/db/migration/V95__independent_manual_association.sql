SET SESSION lock_wait_timeout=10;
CREATE TABLE independent_association (
 id BINARY(16) NOT NULL PRIMARY KEY, warehouse_id BINARY(16) NOT NULL,
 relay_id BINARY(16) NOT NULL, device_id BINARY(16) NOT NULL, task_id BINARY(16) NOT NULL,
 target_order_id BINARY(16) NOT NULL, supervisor_id BINARY(16) NOT NULL, physical_check_id BINARY(16) NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_text MEDIUMTEXT NOT NULL, context_text MEDIUMTEXT NOT NULL, receipt_text MEDIUMTEXT NOT NULL,
 recorded_at DATETIME(3) NOT NULL,
 archive_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 UNIQUE KEY independent_association_task(warehouse_id,device_id,task_id),
 UNIQUE KEY independent_association_target(warehouse_id,target_order_id),
 FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), FOREIGN KEY(relay_id) REFERENCES original_relay(id),
 FOREIGN KEY(device_id) REFERENCES device(id), FOREIGN KEY(target_order_id) REFERENCES outbound_order(id),
 FOREIGN KEY(supervisor_id) REFERENCES app_user(id), FOREIGN KEY(physical_check_id) REFERENCES independent_physical_check(id),
 CHECK(request_sha256 REGEXP '^[0-9a-f]{64}$'), CHECK(context_sha256 REGEXP '^[0-9a-f]{64}$'),
 CHECK(archive_sha256 REGEXP '^[0-9a-f]{64}$'),
 CHECK(JSON_VALID(request_text)), CHECK(JSON_VALID(context_text)), CHECK(JSON_VALID(receipt_text))
) ENGINE=InnoDB;
DELIMITER $$
CREATE TRIGGER independent_association_no_update BEFORE UPDATE ON independent_association FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Independent association is immutable'; END$$
CREATE TRIGGER independent_association_no_delete BEFORE DELETE ON independent_association FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Independent association must be retained'; END$$
DELIMITER ;
