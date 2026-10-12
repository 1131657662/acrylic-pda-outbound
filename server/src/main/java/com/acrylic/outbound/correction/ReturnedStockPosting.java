package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.sync.BarcodeChanges;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The prior correction already reversed actual contributions; this command only restores that stock projection. */
@Component
final class ReturnedStockPosting {
    private final JdbcTemplate jdbc;
    ReturnedStockPosting(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    ObjectNode apply(CorrectionCommand.Write write) {
        var facts = write.anchor().path("facts"); ReturnedStockPolicy.eligible(facts);
        UUID bale = UUID.fromString(facts.at("/dispatch/baleId").asText());
        if (jdbc.queryForObject("SELECT COUNT(*) FROM returned_stock_release WHERE original_dispatch_id=UUID_TO_BIN(?)",
            Integer.class, facts.at("/dispatch/dispatchId").asText())!=0) throw CorrectionCommand.stale();
        if (jdbc.update("UPDATE inventory_position SET book_status='NOT_DEDUCTED',row_version=row_version+1"
            + " WHERE bale_id=? AND book_status='DEDUCTED' AND blocked_reason IS NULL AND row_version=?",
            Ids.bytes(bale), facts.at("/stock/stockVersion").asLong())!=1) throw CorrectionCommand.stale();
        BarcodeChanges.bale(jdbc, bale);
        return SubmissionJson.object().put("availability", "REEVALUATE").put("bookStatus", "NOT_DEDUCTED")
            .put("dispatchCycle", facts.at("/stock/dispatchCycle").asText())
            .put("resultVersion", facts.at("/result/currentResultVersion").asText());
    }
    void record(CorrectionCommand.Write write) {
        var facts = write.anchor().path("facts");
        jdbc.update("INSERT INTO returned_stock_release(case_id,original_dispatch_id,cycle_reopening_id,approval_receipt_id)"
            + " VALUES(?,UUID_TO_BIN(?),UUID_TO_BIN(?),?)", Ids.bytes(write.command().uuid("caseId")),
            facts.at("/dispatch/dispatchId").asText(), facts.at("/reopening/reopeningId").asText(), Ids.bytes(write.receiptId()));
    }
}
