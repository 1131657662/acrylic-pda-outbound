package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Unfiltered raw relations for the independent reverse-candidate consumer. */
final class ReverseCandidateFacts {
    private final JdbcTemplate jdbc;
    ReverseCandidateFacts(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    ObjectNode read() {
        var result = SubmissionJson.object();
        table(result, "submissions", "SELECT id,scope_id,status FROM submission ORDER BY id");
        table(result, "scopes", "SELECT id,warehouse_id,creator_device_id,canonical_order_id,current_submission_id,work_round FROM submission_scope ORDER BY id");
        table(result, "payloads", "SELECT submission_id,payload_sha256,expected_count,received_at FROM submission_payload ORDER BY submission_id");
        table(result, "mappings", "SELECT id,scope_id,warehouse_id,mapping_version,canonical_order_id,origin FROM temporary_source_mapping ORDER BY id");
        table(result, "orders", "SELECT id,warehouse_id,source,order_no,status,current_revision,row_version,created_at FROM outbound_order ORDER BY id");
        table(result, "revisions", "SELECT id,order_id,revision,vehicle_no,container_no,planned_total FROM order_revision ORDER BY id");
        table(result, "lines", "SELECT revision_id,product_id FROM order_line ORDER BY revision_id,product_id");
        table(result, "assignments", "SELECT id,order_id FROM order_assignment ORDER BY id");
        table(result, "pcWork", "SELECT id,order_id FROM pc_work ORDER BY id");
        table(result, "results", "SELECT order_id,submission_id,result_version FROM order_result_version ORDER BY order_id,result_version");
        table(result, "formalScopes", "SELECT id,source_order_id FROM submission_scope WHERE source_order_id IS NOT NULL ORDER BY id");
        return result;
    }

    ObjectNode businessState() {
        var result = read();
        table(result, "commitments", "SELECT bale_id,order_id,submission_id,dispatch_cycle,state FROM bale_commitment ORDER BY bale_id");
        table(result, "dispatches", "SELECT id,bale_id,order_id,submission_id FROM dispatch_record ORDER BY id");
        table(result, "ledger", "SELECT id,dispatch_id,bale_id,quantity_delta,weight_delta_g FROM inventory_ledger ORDER BY id");
        table(result, "decisions", "SELECT scope_id,decision_version FROM temporary_decision_state ORDER BY scope_id");
        return result;
    }

    private void table(ObjectNode result, String name, String sql) {
        var rows = result.putArray(name);
        jdbc.queryForList(sql).forEach(row -> rows.add(convert(row)));
    }

    private ObjectNode convert(Map<String, Object> row) {
        var result = SubmissionJson.object();
        row.forEach((name, value) -> {
            if (value == null) result.putNull(name);
            else if (value instanceof byte[] bytes) {
                var buffer = java.nio.ByteBuffer.wrap(bytes);
                result.put(name, new UUID(buffer.getLong(), buffer.getLong()).toString());
            } else if (value instanceof Timestamp time) result.put(name, time.toInstant().toString());
            else if (value instanceof java.time.LocalDateTime time) result.put(name, time.toInstant(ZoneOffset.UTC).toString());
            else result.put(name, value.toString());
        });
        return result;
    }
}
