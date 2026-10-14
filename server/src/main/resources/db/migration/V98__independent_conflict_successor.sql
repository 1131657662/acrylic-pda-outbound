SET SESSION lock_wait_timeout=10;
CREATE TABLE independent_conflict_successor (
 predecessor_id BINARY(16) NOT NULL PRIMARY KEY, successor_id BINARY(16) NOT NULL,
 UNIQUE KEY independent_conflict_single_predecessor(successor_id),
 FOREIGN KEY(predecessor_id) REFERENCES independent_conflict_decision(id),
 FOREIGN KEY(successor_id) REFERENCES independent_conflict_decision(id), CHECK(predecessor_id<>successor_id)
) ENGINE=InnoDB;
CREATE TABLE independent_conflict_extension_claim (
 decision_id BINARY(16) NOT NULL, warehouse_id BINARY(16) NOT NULL, bale_id BINARY(16) NOT NULL, dispatch_cycle BIGINT NOT NULL,
 PRIMARY KEY(decision_id,warehouse_id,bale_id,dispatch_cycle),
 KEY independent_conflict_extension_root(warehouse_id,bale_id,dispatch_cycle),
 FOREIGN KEY(decision_id) REFERENCES independent_conflict_decision(id),
 FOREIGN KEY(warehouse_id,bale_id,dispatch_cycle) REFERENCES independent_conflict_claim(warehouse_id,bale_id,dispatch_cycle),
 CHECK(dispatch_cycle>0)
) ENGINE=InnoDB;
DELIMITER $$
CREATE TRIGGER independent_conflict_successor_no_update BEFORE UPDATE ON independent_conflict_successor FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflict successor is immutable'; END$$
CREATE TRIGGER independent_conflict_successor_no_delete BEFORE DELETE ON independent_conflict_successor FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflict successor must be retained'; END$$
CREATE TRIGGER independent_conflict_extension_claim_no_update BEFORE UPDATE ON independent_conflict_extension_claim FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflict extension claim is immutable'; END$$
CREATE TRIGGER independent_conflict_extension_claim_no_delete BEFORE DELETE ON independent_conflict_extension_claim FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflict extension claim must be retained'; END$$
DELIMITER ;
