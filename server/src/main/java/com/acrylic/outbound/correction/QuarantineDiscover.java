package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;

final class QuarantineDiscover implements CorrectionCommand.Operation {
    private final QuarantineContext context;
    private final ReconciliationContext guard;
    private final CorrectionStore store;
    private final ReconciliationRequestStore requests;
    private final JdbcTemplate jdbc;
    QuarantineDiscover(QuarantineContext context,ReconciliationContext guard,CorrectionStore store,ReconciliationRequestStore requests,JdbcTemplate jdbc) {
        this.context=context; this.guard=guard; this.store=store; this.requests=requests; this.jdbc=jdbc;
    }
    @Override public String commandType() { return "RECONCILE_QUARANTINE_DISCOVER"; }
    @Override public void beforeLocks(CorrectionCommand.Input input) { guard.beforeLocks(input); }
    @Override public JsonNode lockContext(CorrectionCommand.Input input,JsonNode anchor) {
        return context.read(input.uuid("warehouseId"),input.uuid("baleId"),null,true);
    }
    @Override public JsonNode execute(CorrectionCommand.Write write) {
        ReconciliationPolicy.permission(write.actor()); var facts=write.anchor().path("facts"); QuarantinePolicy.eligible(facts);
        String bale=write.anchor().path("baleId").asText(); String disposition=write.anchor().path("dispositionId").asText();
        if(jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_origin WHERE disposition_id=UUID_TO_BIN(?) AND bale_id=UUID_TO_BIN(?)",
            Integer.class,disposition,bale)!=0) throw CorrectionCommand.stale();
        UUID id=Ids.next(); store.insertReconciliation(id,write.command().uuid("warehouseId"),"QUARANTINE_RELEASE",
            UUID.fromString(bale),null,facts.at("/stock/dispatchCycle").asInt(),null,null,facts.at("/stock/stockVersion").asLong(),write.recordedAt());
        return SubmissionJson.object().put("caseId",id.toString()).put("status","OPEN").put("kind","QUARANTINE_RELEASE")
            .put("baleId",bale).put("dispositionId",disposition).put("factsSha256",write.anchor().path("factsSha256").asText()).put("availability","UNCHANGED");
    }
    @Override public void afterReceipt(CorrectionCommand.Write write,JsonNode decision) {
        requests.discovered(write,decision);
        jdbc.update("INSERT INTO quarantine_origin(case_id,disposition_id,bale_id,original_receipt_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",
            decision.path("caseId").asText(),decision.path("dispositionId").asText(),decision.path("baleId").asText(),
            write.anchor().at("/facts/origin/originalReceipt/receiptId").asText());
    }
}
