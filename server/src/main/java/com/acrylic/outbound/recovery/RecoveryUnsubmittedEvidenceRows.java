package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Reads the complete original set and referenced objects without hiding broken parents or extra items. */
final class RecoveryUnsubmittedEvidenceRows {
    private final JdbcTemplate jdbc;
    RecoveryUnsubmittedEvidenceRows(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    ObjectNode read(RecoverySourceIdentity source, RecoveryUnsubmittedEvidence.Expected expected) {
        var sets = jdbc.query("""
            SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(warehouse_id) warehouseId,manifest_bytes,set_sha256,
             CAST(capture_high_watermark AS CHAR) captureHighWatermark,CAST(audit_through_seq AS CHAR) auditThroughSeq,
             CAST(order_scan_high_watermark AS CHAR) orderScanHighWatermark,CAST(item_count AS CHAR) itemCount,
             BIN_TO_UUID(stop_object_id) stopObjectId
            FROM source_evidence_set WHERE scope_id=? AND submission_id=? AND work_round=? LIMIT 2
            """, (rs, index) -> {
                var row = SubmissionJson.object().put("id", rs.getString("id")).put("warehouseId", rs.getString("warehouseId"))
                    .put("manifestBytesSha256", sha256(rs.getBytes("manifest_bytes"))).put("setSha256", rs.getString("set_sha256"))
                    .put("captureHighWatermark", rs.getString("captureHighWatermark")).put("auditThroughSeq", rs.getString("auditThroughSeq"))
                    .put("orderScanHighWatermark", rs.getString("orderScanHighWatermark")).put("itemCount", rs.getString("itemCount"))
                    .put("stopObjectId", rs.getString("stopObjectId"));
                require(Arrays.equals(rs.getBytes("manifest_bytes"), expected.manifestBytes()), "RECOVERY_UNSUBMITTED_MANIFEST"); return row;
            }, Ids.bytes(source.scopeId()), Ids.bytes(source.submissionId()), source.workRound());
        require(sets.size() == 1, "RECOVERY_UNSUBMITTED_SET"); var set = sets.getFirst();
        require(uuid(set, "warehouseId").equals(source.warehouseId()), "RECOVERY_UNSUBMITTED_SET_SOURCE");
        stop(set, source, expected); set.put("evidenceItemsSha256", items(uuid(set, "id"), source));
        set.remove(List.of("warehouseId", "stopObjectId")); RecoveryUnsubmittedEvidence.verify(set, expected); return set;
    }

    private void stop(ObjectNode set, RecoverySourceIdentity source, RecoveryUnsubmittedEvidence.Expected expected) {
        var rows = jdbc.query("""
            SELECT kind,bytes_sha256,byte_count FROM source_evidence_object
            WHERE id=? AND warehouse_id=? AND scope_id=? AND submission_id=? AND work_round=? AND origin_device_id=? AND uploaded_by=?
            """, (rs, index) -> rs.getString(1).equals("STOP_PROOF") && rs.getString(2).equals(sha256(expected.stopProofBytes()))
                && rs.getLong(3) == expected.stopProofBytes().length, Ids.bytes(uuid(set, "stopObjectId")), Ids.bytes(source.warehouseId()),
            Ids.bytes(source.scopeId()), Ids.bytes(source.submissionId()), source.workRound(), Ids.bytes(source.deviceId()), Ids.bytes(source.operatorId()));
        require(rows.size() == 1 && rows.getFirst(), "RECOVERY_UNSUBMITTED_STOP_OBJECT");
        set.put("stopProofSha256", sha256(expected.stopProofBytes()));
    }

    private String items(UUID set, RecoverySourceIdentity source) {
        var rows = jdbc.query("""
            SELECT i.item_kind itemKind,BIN_TO_UUID(i.original_record_id) originalRecordId,BIN_TO_UUID(o.original_object_id) originalObjectId,
             o.kind objectKind,CAST(i.original_device_seq AS CHAR) originalDeviceSeq,i.record_sha256 recordSha256,
             BIN_TO_UUID(i.bale_id) baleId,CAST(i.original_cycle AS CHAR) originalCycle,BIN_TO_UUID(i.capture_id) captureId,i.record_status recordStatus,
             o.bytes_sha256 objectSha256,CAST(o.byte_count AS CHAR) objectByteCount,
             BIN_TO_UUID(i.warehouse_id) itemWarehouse,BIN_TO_UUID(i.scope_id) itemScope,BIN_TO_UUID(i.submission_id) itemSubmission,i.work_round itemRound,
             BIN_TO_UUID(o.warehouse_id) objectWarehouse,BIN_TO_UUID(o.scope_id) objectScope,BIN_TO_UUID(o.submission_id) objectSubmission,o.work_round objectRound,
             BIN_TO_UUID(o.origin_device_id) objectDevice,BIN_TO_UUID(o.uploaded_by) objectOperator
            FROM source_evidence_item i LEFT JOIN source_evidence_object o ON o.id=i.object_id WHERE i.set_id=?
            ORDER BY i.item_kind,i.original_record_id LIMIT 65537
            """, (rs, index) -> {
                require(source.warehouseId().toString().equals(rs.getString("itemWarehouse"))
                    && source.scopeId().toString().equals(rs.getString("itemScope")) && source.submissionId().toString().equals(rs.getString("itemSubmission"))
                    && rs.getLong("itemRound") == source.workRound() && source.warehouseId().toString().equals(rs.getString("objectWarehouse"))
                    && source.scopeId().toString().equals(rs.getString("objectScope")) && source.submissionId().toString().equals(rs.getString("objectSubmission"))
                    && rs.getLong("objectRound") == source.workRound() && source.deviceId().toString().equals(rs.getString("objectDevice"))
                    && source.operatorId().toString().equals(rs.getString("objectOperator")), "RECOVERY_UNSUBMITTED_ITEM_SOURCE");
                var row = SubmissionJson.object();
                for (String key : List.of("itemKind", "originalRecordId", "originalObjectId", "objectKind", "originalDeviceSeq", "recordSha256",
                        "baleId", "originalCycle", "captureId", "recordStatus", "objectSha256", "objectByteCount")) row.put(key, rs.getString(key));
                return row;
            }, Ids.bytes(set));
        require(rows.size() <= 65536, "RECOVERY_UNSUBMITTED_ITEM_LIMIT");
        var array = SubmissionJson.object().putArray("items"); rows.forEach(array::add); return SubmissionJson.sha(array);
    }
}
