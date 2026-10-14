SET SESSION lock_wait_timeout=10;
CREATE TABLE independent_association_conflict (
 association_id BINARY(16) NOT NULL PRIMARY KEY,
 decision_id BINARY(16) NOT NULL,
 UNIQUE KEY independent_association_conflict_decision(decision_id),
 FOREIGN KEY(association_id) REFERENCES independent_association(id),
 FOREIGN KEY(decision_id) REFERENCES independent_conflict_decision(id)
) ENGINE=InnoDB;
DELIMITER $$
CREATE TRIGGER independent_association_conflict_no_update BEFORE UPDATE ON independent_association_conflict FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Association conflict reference is immutable'; END$$
CREATE TRIGGER independent_association_conflict_no_delete BEFORE DELETE ON independent_association_conflict FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Association conflict reference must be retained'; END$$
DELIMITER ;
