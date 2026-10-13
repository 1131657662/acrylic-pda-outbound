package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(prefix="outbound.corrections.commands",name="enabled",havingValue="true")
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
class QuarantineReadService {
    private final CorrectionCommand.Identity identity;
    private final QuarantineContext contexts;
    private final JdbcTemplate jdbc;
    QuarantineReadService(CorrectionCommand.Identity identity,QuarantineContext contexts,JdbcTemplate jdbc) {
        this.identity=identity; this.contexts=contexts; this.jdbc=jdbc;
    }
    JsonNode context(UUID warehouse,UUID bale,UUID caseId,String kind) {
        if(!java.util.Set.of("RECONCILE_QUARANTINE_DISCOVER","RECONCILE_QUARANTINE_SUBMIT","RECONCILE_QUARANTINE_APPROVE").contains(kind)
            || (kind.endsWith("_DISCOVER") ? bale==null || caseId!=null : caseId==null || bale!=null)) throw CorrectionCommand.missing();
        var actor=identity.current(warehouse,CorrectionCommand.requiresApproval(kind));
        if(!CorrectionCommand.requiresApproval(kind) && !actor.permissions().contains("inventory:reconcile")) throw CorrectionCommand.denied();
        var context=contexts.read(warehouse,bale,caseId,false);
        if(kind.endsWith("_SUBMIT") && !actor.id().toString().equals(context.at("/discoveredReceipt/actor/principalId").asText())) throw CorrectionCommand.missing();
        var result=SubmissionJson.object(); result.set("context",context);
        result.set("expected",ReconciliationContext.expected(context,actor.authVersion())); result.set("capabilities",CorrectionReceiptCodec.capabilities());
        return result;
    }

    record Cursors(UUID candidate,UUID caseId) { }
    byte[] original(UUID warehouse,UUID bale,UUID caseId,String kind) {
        return contexts.original(context(warehouse,bale,caseId,kind).path("context"));
    }
    JsonNode list(UUID warehouse,boolean review,Cursors cursors) {
        var actor=identity.current(warehouse,review);
        if(!review && !actor.permissions().contains("inventory:reconcile")) throw CorrectionCommand.denied();
        var result=SubmissionJson.object(); var candidates=result.putArray("candidates"); var cases=result.putArray("cases");
        var rows=jdbc.queryForList("""
            SELECT BIN_TO_UUID(b.id) baleId,b.barcode,BIN_TO_UUID(COALESCE(d.id,pd.id,fd.id)) dispositionId
            FROM bale_commitment k JOIN bale b ON b.id=k.bale_id
              LEFT JOIN temporary_physical_disposition d ON d.id=k.disposition_id
              LEFT JOIN pc_disposition pd ON pd.id=k.pc_disposition_id
              LEFT JOIN physical_disposition_evidence fd ON fd.id=k.disposition_evidence_id AND fd.applied_submission_id IS NOT NULL
            WHERE b.warehouse_id=? AND k.state='QUARANTINED'
              AND ((d.id IS NOT NULL AND d.warehouse_id=b.warehouse_id) OR pd.id IS NOT NULL OR fd.id IS NOT NULL)
              AND (? IS NULL OR b.id<UUID_TO_BIN(?))
              AND NOT EXISTS(SELECT 1 FROM quarantine_origin q WHERE q.bale_id=b.id
                AND (q.disposition_id=d.id OR q.pc_disposition_id=pd.id OR q.formal_disposition_id=fd.id))
            ORDER BY b.id DESC LIMIT 51
            """,Ids.bytes(warehouse),text(cursors.candidate()),text(cursors.candidate()));
        rows.stream().limit(50).forEach(row->{ var item=candidates.addObject(); row.forEach((key,value)->item.put(key,value.toString())); });
        var caseRows=jdbc.queryForList("""
            SELECT BIN_TO_UUID(r.id) caseId,BIN_TO_UUID(r.bale_id) baleId,r.status,r.kind
            FROM inventory_reconciliation r JOIN quarantine_origin o ON o.case_id=r.id
              JOIN reconciliation_request q ON q.case_id=r.id JOIN correction_command_receipt c ON c.id=q.opened_receipt_id
            WHERE r.warehouse_id=? AND (?=true OR c.actor_id=?) AND (? IS NULL OR r.id<UUID_TO_BIN(?)) ORDER BY r.id DESC LIMIT 51
            """,Ids.bytes(warehouse),review,Ids.bytes(actor.id()),text(cursors.caseId()),text(cursors.caseId()));
        for(var row:caseRows.stream().limit(50).toList()) {
            var item=cases.addObject(); row.forEach((key,value)->item.put(key,value.toString()));
        }
        if(rows.size()>50) result.put("nextCursor",candidates.get(49).path("baleId").asText()); else result.putNull("nextCursor");
        if(caseRows.size()>50) result.put("nextCaseCursor",cases.get(49).path("caseId").asText()); else result.putNull("nextCaseCursor");
        result.set("capabilities",CorrectionReceiptCodec.capabilities()); return result;
    }
    private String text(UUID id) { return id==null?null:id.toString(); }
}
