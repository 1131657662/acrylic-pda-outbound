package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** PC isolation is proven by original database approvals and application, never a fabricated TEMP signature. */
@Component
final class PcQuarantineOrigin {
    private final JdbcTemplate jdbc;
    private final PcQuarantineProof proofs;
    PcQuarantineOrigin(JdbcTemplate jdbc,PcQuarantineProof proofs) { this.jdbc=jdbc; this.proofs=proofs; }

    ObjectNode read(UUID bale,UUID caseId) {
        String filter=caseId==null ? "d.id IN (SELECT pc_disposition_id FROM bale_commitment WHERE bale_id=?)"
            : "d.id IN (SELECT pc_disposition_id FROM quarantine_origin WHERE case_id=? AND bale_id=?)";
        var rows=jdbc.queryForList("""
            SELECT JSON_OBJECT('sourceKind','PC','dispositionId',BIN_TO_UUID(d.id),'baleId',BIN_TO_UUID(e.bale_id),
              'warehouseId',BIN_TO_UUID(w.warehouse_id),'orderId',BIN_TO_UUID(w.order_id),'workId',BIN_TO_UUID(w.id),
              'cycle',CAST(d.expected_cycle AS CHAR),'action',d.action,'closeMode',true,'sourceClosed',p.status='APPLIED',
              'closeReason',d.physical_evidence,'planId',BIN_TO_UUID(p.id),'caseId',BIN_TO_UUID(c.id),
              'entryId',BIN_TO_UUID(e.id),'recordSha256',d.record_sha256,'entryStatus',e.status,
              'entryCycle',CAST(e.dispatch_cycle AS CHAR),'entryDispositionId',BIN_TO_UUID(e.disposition_id)) data
            FROM pc_disposition d JOIN pc_entry e ON e.id=d.entry_id JOIN pc_exception_plan p ON p.id=d.plan_id
              JOIN pc_exception_case c ON c.id=p.case_id JOIN pc_work w ON w.id=c.work_id AND w.id=e.work_id
            WHERE
            """+filter,caseId==null ? new Object[]{Ids.bytes(bale)} : new Object[]{Ids.bytes(caseId),Ids.bytes(bale)});
        if(rows.isEmpty()) return null;
        if(rows.size()!=1) throw CorrectionCommand.corrupt();
        var origin=(ObjectNode)SubmissionJson.parse(rows.getFirst().get("data").toString());
        if(!bale.toString().equals(origin.path("baleId").asText())) throw CorrectionCommand.corrupt();
        var proof=proofs.read(origin); origin.set("originalReceipt",proof);
        origin.put("originalReceiptSha256",SubmissionJson.sha(proof));
        if(caseId!=null) {
            String recorded=jdbc.queryForObject("SELECT pc_evidence_sha256 FROM quarantine_origin WHERE case_id=?",String.class,Ids.bytes(caseId));
            if(!origin.path("originalReceiptSha256").asText().equals(recorded)) throw CorrectionCommand.corrupt();
        }
        return origin;
    }
}
