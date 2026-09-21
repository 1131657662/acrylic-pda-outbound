package com.acrylic.outbound.foundationmigration;

import com.acrylic.outbound.common.Ids;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Deliberately minimal SQL facts exercise FK/CHECK, never evidence verification or a product writer. */
final class SourceConstraintData {
    final JdbcTemplate jdbc;
    final Map<String, Object> source;
    final byte[] object = Ids.bytes(Ids.next());
    final byte[] set = Ids.bytes(Ids.next());

    SourceConstraintData(JdbcTemplate jdbc, int offset) {
        this.jdbc = jdbc;
        source = jdbc.queryForMap("SELECT c.id scope,c.warehouse_id warehouse,s.id submission,s.work_round round, "
                + "s.freeze_device_id device,s.operator_id actor FROM submission_scope c JOIN submission s ON s.scope_id=c.id "
                + "WHERE c.creator_device_id IS NOT NULL AND c.canonical_order_id IS NULL ORDER BY c.id LIMIT 1 OFFSET ?", offset);
    }

    void evidence() {
        jdbc.update("INSERT INTO source_evidence_object(id,warehouse_id,scope_id,submission_id,work_round,kind,original_object_id,"
                + "bytes_sha256,byte_count,file_key,origin_device_id,uploaded_by,received_at) "
                + "VALUES(?,?,?,?,?,'STOP_PROOF',?, ?,2,'synthetic-not-complete',?,?,UTC_TIMESTAMP(3))",
                object, source.get("warehouse"), source.get("scope"), source.get("submission"), source.get("round"),
                Ids.bytes(Ids.next()), "a".repeat(64), source.get("device"), source.get("actor"));
        jdbc.update("INSERT INTO source_evidence_set(id,warehouse_id,scope_id,submission_id,work_round,stop_object_id,manifest_bytes,"
                + "set_sha256,capture_high_watermark,audit_through_seq,item_count,received_at) VALUES(?,?,?,?,?,?,?, ?,0,1,0,UTC_TIMESTAMP(3))",
                set, source.get("warehouse"), source.get("scope"), source.get("submission"), source.get("round"),
                object, new byte[]{123,125}, "b".repeat(64));
    }

    byte[] bale() {
        return jdbc.queryForObject("SELECT b.id FROM bale b WHERE b.warehouse_id=? "
                + "AND NOT EXISTS(SELECT 1 FROM bale_commitment k WHERE k.bale_id=b.id) ORDER BY b.id LIMIT 1", byte[].class, source.get("warehouse"));
    }

    void reserve(byte[] bale, byte[] order, byte[] scope) {
        jdbc.update("INSERT INTO bale_commitment(bale_id,order_id,dispatch_cycle,state,created_at,source_scope_id,source_evidence_set_id,source_warehouse_id) "
                + "VALUES(?,?,1,'SOURCE_RESERVED',UTC_TIMESTAMP(3),?,?,?)", bale, order, scope, set, source.get("warehouse"));
    }

    void item(byte[] bale) {
        jdbc.update("INSERT INTO source_evidence_item(set_id,warehouse_id,scope_id,submission_id,work_round,item_kind,original_record_id,"
                + "object_id,original_device_seq,record_sha256,bale_id,original_cycle,capture_id,record_status) VALUES(?,?,?,?,?,'SCAN',?,?,1,?,?,1,?,'ACTIVE')",
                set, source.get("warehouse"), source.get("scope"), source.get("submission"), source.get("round"), Ids.bytes(Ids.next()),
                object, "c".repeat(64), bale, Ids.bytes(Ids.next()));
    }
}
