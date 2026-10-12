package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Bounded discrepancy discovery and immutable request context for the product maker/checker. */
@Service
@ConditionalOnProperty(prefix="outbound.corrections.commands", name="enabled", havingValue="true")
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
class ReconciliationReadService {
    private final CorrectionCommand.Identity identity;
    private final ReconciliationContext contexts;
    private final JdbcTemplate jdbc;
    ReconciliationReadService(CorrectionCommand.Identity identity, ReconciliationContext contexts, JdbcTemplate jdbc) {
        this.identity = identity; this.contexts = contexts; this.jdbc = jdbc;
    }

    JsonNode context(UUID warehouse, UUID dispatch, UUID caseId, String kind) {
        if (!ReconciliationContext.isBusiness(kind)) throw CorrectionCommand.missing();
        if (kind.equals("RECONCILE_DISCOVER") ? dispatch==null || caseId!=null : caseId==null || dispatch!=null)
            throw CorrectionCommand.missing();
        var actor = identity.current(warehouse, CorrectionCommand.requiresApproval(kind));
        if (!CorrectionCommand.requiresApproval(kind) && !actor.permissions().contains("inventory:reconcile")) throw CorrectionCommand.denied();
        var context = contexts.read(warehouse, dispatch, caseId, false);
        if (kind.equals("RECONCILE_SUBMIT") && !actor.id().toString().equals(context.at("/discoveredReceipt/actor/principalId").asText()))
            throw CorrectionCommand.missing();
        var result = SubmissionJson.object(); result.set("context", context);
        result.set("expected", ReconciliationContext.expected(context, actor.authVersion()));
        result.set("capabilities", CorrectionReceiptCodec.capabilities()); return result;
    }

    record Page(UUID warehouse, boolean review, UUID before, UUID beforeCase) { }

    JsonNode list(Page page) {
        UUID warehouse = page.warehouse(); boolean review = page.review(); UUID before = page.before();
        var actor = identity.current(warehouse, review); var result = SubmissionJson.object();
        if (!review && !actor.permissions().contains("inventory:reconcile")) throw CorrectionCommand.denied();
        var candidates = result.putArray("candidates");
        var candidateRows = jdbc.queryForList("""
            SELECT BIN_TO_UUID(d.id) dispatchId,BIN_TO_UUID(d.order_id) orderId,b.barcode
            FROM dispatch_record d JOIN outbound_order o ON o.id=d.order_id JOIN bale b ON b.id=d.bale_id
            WHERE o.warehouse_id=? AND o.status='COMPLETED' AND (? IS NULL OR d.id<UUID_TO_BIN(?))
              AND NOT EXISTS(SELECT 1 FROM inventory_ledger l WHERE l.dispatch_id=d.id)
              AND NOT EXISTS(SELECT 1 FROM correction_ledger l WHERE l.dispatch_id=d.id)
              AND NOT EXISTS(SELECT 1 FROM inventory_reconciliation r JOIN reconciliation_request q ON q.case_id=r.id
                WHERE r.dispatch_id=d.id AND r.status<>'RESOLVED')
            ORDER BY d.id DESC LIMIT 51
            """, Ids.bytes(warehouse), before == null ? null : before.toString(), before == null ? null : before.toString());
        candidateRows.stream().limit(50).forEach(row -> { var item = candidates.addObject(); row.forEach((key,value) -> item.put(key,value.toString())); });
        var cases = result.putArray("cases");
        var caseRows = cases(page, actor.id());
        caseRows.stream().limit(50).forEach(row -> { var item = cases.addObject(); row.forEach((key,value) -> item.put(key,value.toString())); });
        if (candidateRows.size() > 50) result.put("nextCursor", candidates.get(49).path("dispatchId").asText());
        else result.putNull("nextCursor");
        if (caseRows.size() > 50) result.put("nextCaseCursor", cases.get(49).path("caseId").asText());
        else result.putNull("nextCaseCursor");
        result.set("capabilities", CorrectionReceiptCodec.capabilities()); return result;
    }

    private java.util.List<java.util.Map<String,Object>> cases(Page page, UUID actor) {
        return jdbc.queryForList("""
            SELECT BIN_TO_UUID(r.id) caseId,BIN_TO_UUID(r.dispatch_id) dispatchId,r.status
            FROM inventory_reconciliation r JOIN reconciliation_request q ON q.case_id=r.id
            WHERE r.warehouse_id=? AND (? IS NULL OR r.id<UUID_TO_BIN(?))
              AND (?=true OR EXISTS(SELECT 1 FROM correction_command_receipt c
              WHERE c.id=q.opened_receipt_id AND c.actor_id=?)) ORDER BY r.id DESC LIMIT 51
            """, Ids.bytes(page.warehouse()), page.beforeCase()==null?null:page.beforeCase().toString(),
            page.beforeCase()==null?null:page.beforeCase().toString(), page.review(), Ids.bytes(actor));
    }
}
