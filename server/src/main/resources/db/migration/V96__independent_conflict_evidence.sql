SET SESSION lock_wait_timeout=10;
CREATE TABLE independent_conflict_decision (
 id BINARY(16) NOT NULL PRIMARY KEY, warehouse_id BINARY(16) NOT NULL,
 relay_id BINARY(16) NOT NULL, device_id BINARY(16) NOT NULL, task_id BINARY(16) NOT NULL, supervisor_id BINARY(16) NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_text MEDIUMTEXT NOT NULL, context_text MEDIUMTEXT NOT NULL, receipt_text MEDIUMTEXT NOT NULL,
 recorded_at DATETIME(3) NOT NULL,
 KEY independent_conflict_original(warehouse_id,device_id,task_id,id),
 FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), FOREIGN KEY(relay_id) REFERENCES original_relay(id),
 FOREIGN KEY(device_id) REFERENCES device(id), FOREIGN KEY(supervisor_id) REFERENCES app_user(id),
 CHECK(request_sha256 REGEXP '^[0-9a-f]{64}$'), CHECK(context_sha256 REGEXP '^[0-9a-f]{64}$'),
 CHECK(JSON_VALID(request_text)), CHECK(JSON_VALID(context_text)), CHECK(JSON_VALID(receipt_text))
) ENGINE=InnoDB;
CREATE TABLE independent_conflict_evidence (
 decision_id BINARY(16) NOT NULL, relay_id BINARY(16) NOT NULL, physical_check_id BINARY(16) NOT NULL,
 PRIMARY KEY(decision_id,relay_id), UNIQUE KEY independent_conflict_check(decision_id,physical_check_id),
 FOREIGN KEY(decision_id) REFERENCES independent_conflict_decision(id), FOREIGN KEY(relay_id) REFERENCES original_relay(id),
 FOREIGN KEY(physical_check_id) REFERENCES independent_physical_check(id)
) ENGINE=InnoDB;
CREATE TABLE independent_conflict_claim (
 warehouse_id BINARY(16) NOT NULL, bale_id BINARY(16) NOT NULL, dispatch_cycle BIGINT NOT NULL,
 decision_id BINARY(16) NOT NULL, barcode VARCHAR(11) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 PRIMARY KEY(warehouse_id,bale_id,dispatch_cycle), KEY independent_conflict_claim_decision(decision_id),
 FOREIGN KEY(warehouse_id) REFERENCES warehouse(id), FOREIGN KEY(bale_id) REFERENCES bale(id),
 FOREIGN KEY(decision_id) REFERENCES independent_conflict_decision(id),
 CHECK(dispatch_cycle>0), CHECK(barcode REGEXP '^[A-Z0-9]{11}$')
) ENGINE=InnoDB;
DELIMITER $$
CREATE TRIGGER independent_conflict_decision_no_update BEFORE UPDATE ON independent_conflict_decision FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflict decision is immutable'; END$$
CREATE TRIGGER independent_conflict_decision_no_delete BEFORE DELETE ON independent_conflict_decision FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflict decision must be retained'; END$$
CREATE TRIGGER independent_conflict_evidence_no_update BEFORE UPDATE ON independent_conflict_evidence FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflict evidence is immutable'; END$$
CREATE TRIGGER independent_conflict_evidence_no_delete BEFORE DELETE ON independent_conflict_evidence FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflict evidence must be retained'; END$$
CREATE TRIGGER independent_conflict_claim_no_update BEFORE UPDATE ON independent_conflict_claim FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflict claim is immutable'; END$$
CREATE TRIGGER independent_conflict_claim_no_delete BEFORE DELETE ON independent_conflict_claim FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflict claim must be retained'; END$$
DELIMITER ;
