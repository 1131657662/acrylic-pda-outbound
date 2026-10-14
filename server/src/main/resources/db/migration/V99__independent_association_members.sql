SET SESSION lock_wait_timeout=10;
CREATE TABLE independent_association_member (
 association_id BINARY(16) NOT NULL, warehouse_id BINARY(16) NOT NULL,
 relay_id BINARY(16) NOT NULL, device_id BINARY(16) NOT NULL, task_id BINARY(16) NOT NULL,
 physical_check_id BINARY(16) NOT NULL, conflict_decision_id BINARY(16) NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, context_text MEDIUMTEXT NOT NULL,
 PRIMARY KEY(association_id,relay_id),
 UNIQUE KEY independent_association_member_task(warehouse_id,device_id,task_id),
 FOREIGN KEY(association_id) REFERENCES independent_association(id), FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),
 FOREIGN KEY(relay_id) REFERENCES original_relay(id), FOREIGN KEY(device_id) REFERENCES device(id),
 FOREIGN KEY(physical_check_id) REFERENCES independent_physical_check(id), FOREIGN KEY(conflict_decision_id) REFERENCES independent_conflict_decision(id),
 CHECK(request_sha256 REGEXP '^[0-9a-f]{64}$'), CHECK(context_sha256 REGEXP '^[0-9a-f]{64}$'), CHECK(JSON_VALID(context_text))
) ENGINE=InnoDB;
DELIMITER $$
CREATE TRIGGER independent_association_member_no_update BEFORE UPDATE ON independent_association_member FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Independent association member is immutable'; END$$
CREATE TRIGGER independent_association_member_no_delete BEFORE DELETE ON independent_association_member FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Independent association member must be retained'; END$$
DELIMITER ;
