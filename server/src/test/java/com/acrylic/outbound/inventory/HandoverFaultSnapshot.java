package com.acrylic.outbound.inventory;

import com.acrylic.outbound.e2averify.E2aSnapshotRows;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** All durable columns, with actual attachment blobs hashed to bound snapshot memory at the capacity limit. */
final class HandoverFaultSnapshot {
    static ObjectNode capture(JdbcTemplate jdbc) {
        var result = SubmissionJson.object(); var rows = new E2aSnapshotRows(jdbc);
        for (String table : jdbc.queryForList("SHOW TABLES", String.class)) {
            if (Set.of("spring_session", "spring_session_attributes", "flyway_schema_history").contains(table)) continue;
            if ("handover_attachment".equals(table)) { attachments(jdbc, result); continue; }
            var columns = jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION", String.class, table);
            var snapshot = result.putObject(table); columns.forEach(snapshot.putArray("columns")::add);
            snapshot.set("rows", rows.rows(table, columns));
        }
        return result;
    }

    private static void attachments(JdbcTemplate jdbc, ObjectNode result) {
        var rows = result.putArray("handover_attachment");
        jdbc.query("""
            SELECT id,case_id,warehouse_id,uploaded_by,upload_key,content_sha256,size_bytes,created_at,
              OCTET_LENGTH(content_bytes),LOWER(SHA2(content_bytes,256)) FROM handover_attachment ORDER BY id
            """, rs -> {
            var row = rows.addArray();
            for (int i = 1; i <= 10; i++) {
                byte[] value = rs.getBytes(i);
                if (value == null) row.addNull(); else row.add(Base64.getEncoder().encodeToString(value));
            }
        });
    }
}
