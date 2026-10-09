package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Original SOURCE_RESERVED rows only; all alternative owners and unavailable stock fail closed. */
final class RecoveryUnsubmittedOccupancyRows {
    private final JdbcTemplate jdbc;
    RecoveryUnsubmittedOccupancyRows(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    ArrayNode read(RecoverySourceIdentity source, JsonNode payload, UUID set) {
        var rows = SubmissionJson.object().putArray("rows"); var seen = new HashSet<UUID>();
        for (var scan : SubmissionJson.array(payload, "scans")) {
            require(Set.of("ACTIVE", "VOID").contains(text(scan, "status")), "RECOVERY_UNSUBMITTED_SCAN");
            if ("VOID".equals(text(scan, "status"))) continue;
            require(seen.add(uuid(scan, "baleId")) && rows.size() < 128, "RECOVERY_UNSUBMITTED_BALES");
            var row = row(scan, source, set); row.set("captureId", scan.path("captureId")); row.set("orderScanSeq", scan.path("orderScanSeq"));
            rows.add(row);
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE source_scope_id=? OR source_evidence_set_id=? OR submission_id=?",
            Long.class, Ids.bytes(source.scopeId()), Ids.bytes(set), Ids.bytes(source.submissionId()));
        require(count != null && count == rows.size(), "RECOVERY_UNSUBMITTED_OWNER_SET"); return rows;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode row(JsonNode scan, RecoverySourceIdentity source, UUID set) {
        var rows = jdbc.query("""
            SELECT BIN_TO_UUID(b.id) baleId,b.barcode,BIN_TO_UUID(b.warehouse_id) warehouseId,CAST(b.dispatch_cycle AS CHAR) currentCycle,
             CAST(k.dispatch_cycle AS CHAR) dispatchCycle,k.state ownerState,BIN_TO_UUID(k.submission_id) ownerSubmissionId,
             BIN_TO_UUID(k.source_scope_id) ownerScopeId,BIN_TO_UUID(k.source_warehouse_id) ownerWarehouseId,
             BIN_TO_UUID(k.order_id) ownerOrderId,BIN_TO_UUID(k.source_evidence_set_id) sourceEvidenceSetId,
             BIN_TO_UUID(k.continuation_id) continuationId,BIN_TO_UUID(k.recovery_package_id) recoveryPackageId,
             BIN_TO_UUID(k.disposition_id) dispositionId,BIN_TO_UUID(k.disposition_evidence_id) dispositionEvidenceId,
             BIN_TO_UUID(k.pc_work_id) pcWorkId,BIN_TO_UUID(k.pc_disposition_id) pcDispositionId,
             BIN_TO_UUID(k.temporary_continuation_id) temporaryContinuationId,BIN_TO_UUID(k.temporary_handover_activation_id) temporaryHandoverActivationId,
             p.upstream_present,p.book_status,p.blocked_reason
            FROM bale b LEFT JOIN bale_commitment k ON k.bale_id=b.id LEFT JOIN inventory_position p ON p.bale_id=b.id WHERE b.id=?
            """, (rs, index) -> {
                var value = SubmissionJson.object();
                for (String field : List.of("baleId", "barcode", "warehouseId", "currentCycle", "dispatchCycle", "ownerState", "ownerSubmissionId",
                        "ownerScopeId", "ownerWarehouseId", "ownerOrderId", "sourceEvidenceSetId", "continuationId", "recoveryPackageId", "dispositionId",
                        "dispositionEvidenceId", "pcWorkId", "pcDispositionId", "temporaryContinuationId", "temporaryHandoverActivationId"))
                    value.put(field, rs.getString(field));
                if (rs.getObject("upstream_present") == null) value.putNull("upstreamPresent"); else value.put("upstreamPresent", rs.getBoolean("upstream_present"));
                return value.put("bookStatus", rs.getString("book_status")).put("blockedReason", rs.getString("blocked_reason"));
            }, Ids.bytes(uuid(scan, "baleId")));
        require(rows.size() == 1, "RECOVERY_UNSUBMITTED_BALE_UNKNOWN"); var value = rows.getFirst();
        require(uuid(value, "warehouseId").equals(source.warehouseId()) && value.path("dispatchCycle").equals(value.path("currentCycle"))
            && text(value, "ownerState").equals("SOURCE_RESERVED") && uuid(value, "ownerScopeId").equals(source.scopeId())
            && uuid(value, "ownerWarehouseId").equals(source.warehouseId()) && uuid(value, "sourceEvidenceSetId").equals(set),
            "RECOVERY_UNSUBMITTED_OWNER");
        available(value, scan); value.remove(List.of("warehouseId", "currentCycle")); return value;
    }

    private void available(JsonNode value, JsonNode scan) {
        require(value.path("upstreamPresent").isBoolean() && value.path("upstreamPresent").booleanValue()
            && text(value, "bookStatus").equals("NOT_DEDUCTED") && value.path("blockedReason").isNull()
            && value.path("barcode").equals(scan.path("barcode")) && value.path("dispatchCycle").equals(scan.path("expectedDispatchCycle")),
            "RECOVERY_UNSUBMITTED_UNAVAILABLE");
        require(text(value, "barcode").matches("[A-Z0-9]{11}"), "RECOVERY_UNSUBMITTED_BARCODE");
        for (String field : List.of("ownerSubmissionId", "ownerOrderId", "continuationId", "recoveryPackageId", "dispositionId",
                "dispositionEvidenceId", "pcWorkId", "pcDispositionId", "temporaryContinuationId", "temporaryHandoverActivationId"))
            require(value.path(field).isNull(), "RECOVERY_UNSUBMITTED_CHAIN");
    }
}
