package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Base64;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Bounded lossless database inventory; fixed tables include empty sets and global FH counters. */
final class RecoveryReleaseInventoryRows {
    private static final List<String> TABLES=List.of(
        "device","pda_session","temporary_authorization","offline_authorization","order_assignment",
        "submission_scope","submission","submission_payload","submission_archive","temporary_work_origin",
        "temporary_source_mapping","source_evidence_set","source_evidence_verification",
        "handover_case","handover_fault_fence","handover_formal_fault_fence","handover_evidence",
        "handover_command_receipt","handover_continuation","temporary_handover_activation_ticket",
        "temporary_handover_activation","temporary_source_continuation","temporary_source_continuation_activation",
        "correction_document","correction_line","correction_effect","correction_ledger",
        "correction_command_receipt","correction_occupancy_restoration","cycle_reopening",
        "bale","bale_commitment","inventory_position","inventory_discrepancy","inventory_reconciliation",
        "inventory_ledger","dispatch_record","order_result_version","order_current_result",
        "pda_audit_event","pda_audit_checkpoint","pda_audit_archive","local_draft_evidence",
        "local_draft_evidence_complete","recovery_package","outbound_order","order_number_counter",
        "order_sync_outbox","sync_stream","barcode_sync_stream","background_job");
    private static final int MAX_ROWS=65536;
    private final JdbcTemplate jdbc;
    private final byte[] warehouse;
    private long bytesRead;

    RecoveryReleaseInventoryRows(JdbcTemplate jdbc,java.util.UUID warehouse) {
        this.jdbc=jdbc; this.warehouse=com.acrylic.outbound.common.Ids.bytes(warehouse);
    }

    ObjectNode read() {
        var result=com.acrylic.outbound.submissions.SubmissionJson.object();
        for(String table:TABLES) result.set(table,table(table));
        return result;
    }

    private ObjectNode table(String table) {
        var result=com.acrylic.outbound.submissions.SubmissionJson.object();
        var columns=result.putArray("columns"); var rows=result.putArray("rows");
        String filter=RecoveryReleaseInventoryQueries.filter(table);
        Object[] parameters=filter.isEmpty()?new Object[]{}:new Object[]{warehouse};
        jdbc.query("SELECT * FROM "+table+filter+" LIMIT "+(MAX_ROWS+1)+" FOR UPDATE",rs -> {
            var metadata=rs.getMetaData();
            for(int i=1;i<=metadata.getColumnCount();i++) columns.add(metadata.getColumnLabel(i));
            readRows(table,rs,rows);
            return null;
        },parameters);
        var ordered=new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        rows.forEach(ordered::add); ordered.sort(java.util.Comparator.comparing(Object::toString));
        rows.removeAll(); ordered.forEach(rows::add);
        result.put("rowCount",Integer.toString(rows.size()));
        if(RecoveryReleaseInventoryQueries.digestOnly(table)) {
            result.put("rowsSha256",RecoveryControlFormat.sha256(RecoveryTemporaryEvidenceMaterial.encode(rows))); result.remove("rows");
        }
        return result;
    }

    private void readRows(String table,java.sql.ResultSet rs,com.fasterxml.jackson.databind.node.ArrayNode rows)
            throws java.sql.SQLException {
        var metadata=rs.getMetaData();
        while(rs.next()) {
            require(rows.size()<MAX_ROWS,"RECOVERY_RELEASE_INVENTORY_LIMIT"); var row=rows.addArray();
            for(int i=1;i<=metadata.getColumnCount();i++) {
                byte[] value=rs.getBytes(i);
                if(value!=null) { bytesRead+=value.length; require(bytesRead<=16777216,"RECOVERY_RELEASE_INVENTORY_BYTES"); }
                if(value==null) row.addNull();
                else if(RecoveryReleaseInventoryQueries.confidential(table,metadata.getColumnLabel(i)))
                    row.add("sha256:"+RecoveryControlFormat.sha256(value));
                else row.add(Base64.getEncoder().encodeToString(value));
            }
        }
    }
}
