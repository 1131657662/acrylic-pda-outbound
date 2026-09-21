package com.acrylic.outbound.inventory;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Synthetic accepted evidence relations, not an evidence-upload or verification service. */
final class SourceFoundationFixture {
    private SourceFoundationFixture() { }
    static String reserve(TemporarySubmissionFixture t,String order) {
        JdbcTemplate j=t.f.jdbc;String object=UUID.randomUUID().toString(),set=UUID.randomUUID().toString();
        j.update("""
            INSERT INTO source_evidence_object(id,warehouse_id,scope_id,submission_id,work_round,kind,original_object_id,bytes_sha256,byte_count,file_key,origin_device_id,uploaded_by,received_at)
            SELECT UUID_TO_BIN(?),c.warehouse_id,c.id,s.id,s.work_round,'STOP_PROOF',UUID_TO_BIN(?),?,2,'synthetic-stop.json',s.freeze_device_id,s.operator_id,UTC_TIMESTAMP(3)
            FROM submission s JOIN submission_scope c ON c.id=s.scope_id WHERE s.id=UUID_TO_BIN(?)
            """,object,object,"0".repeat(64),t.id());
        j.update("""
            INSERT INTO source_evidence_set(id,warehouse_id,scope_id,submission_id,work_round,stop_object_id,manifest_bytes,set_sha256,capture_high_watermark,audit_through_seq,item_count,received_at)
            SELECT UUID_TO_BIN(?),warehouse_id,scope_id,submission_id,work_round,id,CAST('{}' AS BINARY),?,2,7,0,UTC_TIMESTAMP(3)
            FROM source_evidence_object WHERE id=UUID_TO_BIN(?)
            """,set,"0".repeat(64),object);
        j.update("""
            INSERT INTO bale_commitment(bale_id,order_id,dispatch_cycle,state,created_at,source_scope_id,source_evidence_set_id,source_warehouse_id)
            SELECT UUID_TO_BIN(?),UUID_TO_BIN(?),1,'SOURCE_RESERVED',UTC_TIMESTAMP(3),scope_id,id,warehouse_id FROM source_evidence_set WHERE id=UUID_TO_BIN(?)
            """,t.body.at("/scans/0/baleId").asText(),order,set);
        return set;
    }
}
