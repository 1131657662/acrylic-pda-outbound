package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Base64;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Lossless original intake receipts and the entire device audit prefix. No history is generated. */
final class RecoveryReceivedTemporaryHistoryRows {
    private final JdbcTemplate jdbc;
    RecoveryReceivedTemporaryHistoryRows(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    ObjectNode read(RecoverySourceIdentity id, String lock) {
        var result=SubmissionJson.object();
        result.set("intake",query("SELECT * FROM source_evidence_intake_receipt WHERE scope_id=? OR submission_id=? ORDER BY id LIMIT 65537"+lock,
            new Object[]{Ids.bytes(id.scopeId()),Ids.bytes(id.submissionId())}));
        result.set("audit",query("SELECT * FROM pda_audit_event WHERE origin_device_id=? AND local_seq<=? ORDER BY local_seq LIMIT 65537"+lock,
            new Object[]{Ids.bytes(id.deviceId()),id.auditThroughSeq()}));
        require(!result.path("intake").isEmpty() && result.path("audit").size()==id.auditThroughSeq(),"RECOVERY_RECEIVED_HISTORY");
        return result;
    }
    private com.fasterxml.jackson.databind.node.ArrayNode query(String sql, Object[] arguments) {
        var rows=jdbc.query(sql,(rs,n) -> {
            var row=SubmissionJson.object();
            for(int column=1;column<=rs.getMetaData().getColumnCount();column++) {
                String name=rs.getMetaData().getColumnLabel(column); Object value=rs.getObject(column);
                if(value==null) row.putNull(name);
                else if(value instanceof byte[] bytes) row.put(name,Base64.getEncoder().encodeToString(bytes));
                else if(value instanceof java.sql.Timestamp time) row.put(name,time.toInstant().toString());
                else row.put(name,rs.getString(column));
            }
            return row;
        },arguments);
        require(rows.size()<=65536,"RECOVERY_RECEIVED_HISTORY_LIMIT"); var result=SubmissionJson.object().putArray("rows"); rows.forEach(result::add); return result;
    }
}
