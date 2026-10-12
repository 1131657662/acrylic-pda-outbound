package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** One missing debit of the current valid dispatch, with no result, cycle or quarantine mutation. */
@Component
final class ReconciliationPosting {
    private final JdbcTemplate jdbc;
    ReconciliationPosting(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    ObjectNode apply(CorrectionCommand.Write write) {
        var facts = write.anchor().path("facts"); ReconciliationPolicy.missingPosting(facts);
        UUID dispatch = UUID.fromString(facts.at("/dispatch/dispatchId").asText());
        UUID bale = UUID.fromString(facts.at("/dispatch/baleId").asText()); UUID ledger = Ids.next();
        long weight = facts.at("/dispatch/weightG").asLong(); String key = "RECONCILE:MANUAL_DEDUCT:" + dispatch;
        jdbc.update("""
            INSERT INTO correction_ledger(id,dispatch_id,bale_id,correction_line_id,entry_kind,quantity_delta,
              weight_delta_g,business_key,created_at) VALUES(?,?,?,NULL,'MANUAL_DEDUCT',-1,?,?,?)
            """, Ids.bytes(ledger), Ids.bytes(dispatch), Ids.bytes(bale), -weight, key, Timestamp.from(write.recordedAt()));
        if (jdbc.update("UPDATE inventory_position SET book_status='DEDUCTED',row_version=row_version+1"
            + " WHERE bale_id=? AND book_status='NOT_DEDUCTED' AND row_version=?", Ids.bytes(bale),
            facts.at("/stock/stockVersion").asLong()) != 1) throw CorrectionCommand.stale();
        return SubmissionJson.object().put("ledgerId", ledger.toString()).put("businessKey", key)
            .put("quantityDelta", "-1").put("weightDeltaG", Long.toString(-weight))
            .put("resultVersion", facts.at("/result/currentResultVersion").asText())
            .put("dispatchCycle", facts.at("/dispatch/dispatchCycle").asText()).put("availability", "UNCHANGED");
    }
}
