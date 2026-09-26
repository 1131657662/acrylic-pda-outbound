SET SESSION lock_wait_timeout=10;
ALTER TABLE source_evidence_intake_receipt
 ADD UNIQUE KEY uq_evidence_intake_stage_context(principal_id,route_scope,original_key,scope_id,submission_id,work_round,operation_kind);
CREATE TABLE source_evidence_page_item_stage (
 warehouse_id BINARY(16) NOT NULL,
 scope_id BINARY(16) NOT NULL,
 submission_id BINARY(16) NOT NULL,
 work_round BIGINT NOT NULL,
 principal_id BINARY(16) NOT NULL,
 route_scope VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 open_original_key BINARY(16) NOT NULL,
 open_operation_kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'OPEN',
 page_object_id BINARY(16) NOT NULL,
 page_no INT NOT NULL,
 item_ordinal INT NOT NULL,
 item_kind VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 original_record_id BINARY(16) NOT NULL,
 original_object_id BINARY(16) NOT NULL,
 raw_object_id BINARY(16) NOT NULL,
 byte_count BIGINT NOT NULL,
 PRIMARY KEY(scope_id,submission_id,work_round,item_kind,original_record_id),
 UNIQUE KEY uq_evidence_stage_object(scope_id,submission_id,work_round,original_object_id),
 UNIQUE KEY uq_evidence_stage_page_ordinal(page_object_id,item_ordinal),
 KEY ix_evidence_stage_manifest(scope_id,submission_id,work_round,open_original_key,page_no,item_ordinal),
 KEY ix_evidence_stage_open(principal_id,route_scope,open_original_key,scope_id,submission_id,work_round,open_operation_kind),
 FOREIGN KEY(warehouse_id,scope_id) REFERENCES submission_scope(warehouse_id,id),
 FOREIGN KEY(scope_id,submission_id,work_round) REFERENCES submission(scope_id,id,work_round),
 FOREIGN KEY(principal_id,route_scope,open_original_key,scope_id,submission_id,work_round,open_operation_kind)
  REFERENCES source_evidence_intake_receipt(principal_id,route_scope,original_key,scope_id,submission_id,work_round,operation_kind),
 FOREIGN KEY(page_object_id,scope_id,submission_id,work_round)
  REFERENCES source_evidence_object(id,scope_id,submission_id,work_round),
 FOREIGN KEY(raw_object_id,scope_id,submission_id,work_round)
  REFERENCES source_evidence_object(id,scope_id,submission_id,work_round),
 CHECK(work_round>0 AND page_no BETWEEN 1 AND 4096 AND item_ordinal>=0),
 CHECK(item_kind IN ('CAPTURE','SCAN','AUDIT','STOP','ARCHIVE')),
 CHECK(route_scope='r06.e2a.evidence.v1'),
 CHECK(open_operation_kind='OPEN'),
 CHECK(byte_count BETWEEN 1 AND 33554432)
) ENGINE=InnoDB;
CREATE TRIGGER trg_evidence_stage_no_update BEFORE UPDATE ON source_evidence_page_item_stage
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='source evidence staged page items are immutable';
CREATE TRIGGER trg_evidence_stage_no_delete BEFORE DELETE ON source_evidence_page_item_stage
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='source evidence staged page items are immutable';
