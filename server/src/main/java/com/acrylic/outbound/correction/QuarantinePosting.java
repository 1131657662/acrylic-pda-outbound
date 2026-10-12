package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.sync.BarcodeChanges;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
final class QuarantinePosting {
    private final JdbcTemplate jdbc;
    QuarantinePosting(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    ObjectNode apply(CorrectionCommand.Write write) {
        var facts=write.anchor().path("facts"); QuarantinePolicy.eligible(facts); var origin=facts.path("origin");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_release WHERE case_id=?",Integer.class,Ids.bytes(write.command().uuid("caseId")))!=0)
            throw CorrectionCommand.stale();
        UUID bale=UUID.fromString(write.anchor().path("baleId").asText());
        int changed="FORMAL".equals(origin.path("sourceKind").asText()) ? removeFormal(bale,origin)
            : "PC".equals(origin.path("sourceKind").asText()) ? removePc(bale,origin) : jdbc.update("""
            DELETE FROM bale_commitment WHERE bale_id=? AND state='QUARANTINED' AND dispatch_cycle=?
              AND disposition_id=UUID_TO_BIN(?) AND source_scope_id=UUID_TO_BIN(?) AND source_warehouse_id=UUID_TO_BIN(?)
              AND source_evidence_set_id=UUID_TO_BIN(?) AND order_id IS NULL AND submission_id IS NULL
              AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL
              AND pc_work_id IS NULL AND pc_disposition_id IS NULL AND temporary_continuation_id IS NULL AND temporary_handover_activation_id IS NULL
            """,Ids.bytes(bale),origin.path("cycle").asLong(),origin.path("dispositionId").asText(),origin.path("scopeId").asText(),
            origin.path("warehouseId").asText(),origin.path("evidenceSetId").asText());
        if(changed!=1) throw CorrectionCommand.stale();
        BarcodeChanges.bale(jdbc,bale);
        return SubmissionJson.object().put("availability","REEVALUATE").put("dispatchCycle",origin.path("cycle").asText())
            .put("bookStatus",facts.at("/stock/bookStatus").asText()).put("baleId",bale.toString());
    }
    void record(CorrectionCommand.Write write) {
        jdbc.update("INSERT INTO quarantine_release(case_id,approval_receipt_id) VALUES(?,?)",Ids.bytes(write.command().uuid("caseId")),Ids.bytes(write.receiptId()));
    }
    private int removeFormal(UUID bale,com.fasterxml.jackson.databind.JsonNode origin) {
        return jdbc.update("""
            DELETE FROM bale_commitment WHERE bale_id=? AND state='QUARANTINED' AND dispatch_cycle=?
              AND disposition_evidence_id=UUID_TO_BIN(?) AND order_id=UUID_TO_BIN(?) AND submission_id IS NULL
              AND recovery_package_id IS NULL AND continuation_id IS NULL AND pc_disposition_id IS NULL
              AND pc_work_id IS NULL AND disposition_id IS NULL AND source_scope_id IS NULL
              AND source_warehouse_id IS NULL AND source_evidence_set_id IS NULL
              AND temporary_continuation_id IS NULL AND temporary_handover_activation_id IS NULL
            """,Ids.bytes(bale),origin.path("cycle").asLong(),origin.path("dispositionId").asText(),origin.path("orderId").asText());
    }
    private int removePc(UUID bale,com.fasterxml.jackson.databind.JsonNode origin) {
        return jdbc.update("""
            DELETE FROM bale_commitment WHERE bale_id=? AND state='QUARANTINED' AND dispatch_cycle=?
              AND pc_disposition_id=UUID_TO_BIN(?) AND order_id=UUID_TO_BIN(?) AND submission_id IS NULL
              AND recovery_package_id IS NULL AND continuation_id IS NULL AND disposition_evidence_id IS NULL
              AND pc_work_id IS NULL AND disposition_id IS NULL AND source_scope_id IS NULL
              AND source_warehouse_id IS NULL AND source_evidence_set_id IS NULL
              AND temporary_continuation_id IS NULL AND temporary_handover_activation_id IS NULL
            """,Ids.bytes(bale),origin.path("cycle").asLong(),origin.path("dispositionId").asText(),origin.path("orderId").asText());
    }
}
