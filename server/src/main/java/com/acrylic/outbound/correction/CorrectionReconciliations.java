package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Step 7: discrepancy cases (design §2 {@code inventory_reconciliation}, state machine
 * {@code OPEN -> EVIDENCE_PENDING -> REVIEW_READY -> RESOLVED}, conflicts {@code BLOCKED}).
 *
 * <p>What the design fixes: the five statuses, that the case belongs to a warehouse with an optional bale / dispatch /
 * cycle and optional expectations, and the permission {@code inventory:reconcile}. What it does <b>not</b> fix: which
 * business situation opens a case, what evidence each transition needs, and what exactly counts as a conflict. Those
 * are recorded as open policy items; this batch implements the machine itself, so the drivers can be attached without
 * touching the storage or the kernel again.
 *
 * <p>Case handling is deliberately inert towards availability: resolving a case never writes a result version, an
 * effect, a ledger row or a scan snapshot ("处理结论不等于该包可用").
 */
public final class CorrectionReconciliations {
    static final String PERMISSION = "inventory:reconcile";
    private static final List<String> KINDS = List.of("MISSING_PACKAGE", "WEIGHT_MISMATCH", "CYCLE_CONFLICT", "OTHER");

    private CorrectionReconciliations() { }

    private static void requireReconcilePermission(CorrectionCommand.Write write) {
        var permissions=write.actor().path("permissionSnapshot").path("effectivePermissions");
        for (JsonNode permission : permissions) if (PERMISSION.equals(permission.asText())) return;
        throw CorrectionCommand.denied();
    }

    private static String text(JsonNode node,String field,String fallback) {
        var value=node.get(field);
        return value==null || value.isNull()?fallback:value.asText();
    }

    private static Long number(JsonNode node,String field) {
        String value=text(node,field,null);
        return value==null || value.isBlank()?null:Long.parseLong(value);
    }

    private static UUID uuid(JsonNode node,String field) {
        String value=text(node,field,null);
        return value==null || value.isBlank()?null:UUID.fromString(value);
    }

    /** RECONCILE_OPEN: records an OPEN case. No order, no document and no effect - just the case. */
    public static final class Open implements CorrectionCommand.Operation {
        private final CorrectionStore store;
        public Open(CorrectionStore store) { this.store=store; }

        @Override public String commandType() { return "RECONCILE_OPEN"; }

        @Override public JsonNode execute(CorrectionCommand.Write write) {
            requireReconcilePermission(write);
            var body=write.command().body();
            String kind=text(body,"kind","");
            if(!KINDS.contains(kind)) throw new BusinessException(400,"INVALID_CORRECTION_COMMAND","差异类型无效");
            UUID caseId=Ids.next();
            store.insertReconciliation(caseId,UUID.fromString(write.actor().path("warehouseId").asText()),kind,
                uuid(body,"baleId"),uuid(body,"dispatchId"),
                number(body,"cycle")==null?null:number(body,"cycle").intValue(),
                number(body,"expectedResultVersion"),number(body,"expectedLedgerVersion"),
                number(body,"expectedStockStateVersion"),write.recordedAt());
            return SubmissionJson.object().put("caseId",caseId.toString()).put("status","OPEN").put("kind",kind);
        }
    }

    /**
     * RECONCILE_ADVANCE: one step along the machine. {@code blocked=true} moves any non-terminal case to BLOCKED;
     * RESOLVED demands a conclusion, because a resolved case without one cannot be reviewed afterwards.
     */
    public static final class Advance implements CorrectionCommand.Operation {
        private final CorrectionStore store;
        public Advance(CorrectionStore store) { this.store=store; }

        @Override public String commandType() { return "RECONCILE_ADVANCE"; }

        @Override public JsonNode execute(CorrectionCommand.Write write) {
            requireReconcilePermission(write);
            var facts=write.anchor();
            if (java.util.Set.of("MISSING_POSTING", "RETURNED_STOCK", "QUARANTINE_RELEASE").contains(facts.path("kind").asText()))
                throw new BusinessException(409,"RECONCILIATION_EVIDENCE_REQUIRED","账务差异须经原证据提交及独立主管审批，不能仅流转状态");
            UUID caseId=UUID.fromString(facts.path("caseId").asText());
            String status=facts.path("status").asText();
            var body=write.command().body();
            String conclusion=text(body,"conclusion",null);
            boolean blocked=body.path("blocked").asBoolean(false);
            String next;
            if(blocked) {
                if("RESOLVED".equals(status) || "BLOCKED".equals(status)) throw CorrectionCommand.stale();
                if(conclusion==null || conclusion.isBlank()) throw new BusinessException(400,"INVALID_CORRECTION_COMMAND","标记冲突必须写原因");
                next="BLOCKED";
            } else {
                next=switch(status) {
                    case "OPEN" -> "EVIDENCE_PENDING";
                    case "EVIDENCE_PENDING" -> "REVIEW_READY";
                    case "REVIEW_READY" -> "RESOLVED";
                    default -> throw CorrectionCommand.stale();
                };
                if("RESOLVED".equals(next) && (conclusion==null || conclusion.isBlank()))
                    throw new BusinessException(400,"INVALID_CORRECTION_COMMAND","结案必须写处理结论");
            }
            if(store.advanceReconciliation(caseId,status,next,conclusion,write.recordedAt())!=1)
                throw CorrectionCommand.stale();
            return SubmissionJson.object().put("caseId",caseId.toString())
                .put("previousStatus",status).put("status",next)
                .put("conclusion",conclusion==null?"null":conclusion)
                .put("availability","UNCHANGED");
        }
    }
}
