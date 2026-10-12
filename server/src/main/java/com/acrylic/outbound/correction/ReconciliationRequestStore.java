package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Original signed request references; receipts remain the single source for immutable command/evidence bytes. */
@Repository
class ReconciliationRequestStore {
    private final JdbcTemplate jdbc;
    private final CorrectionReadReceipt verifier;
    ReconciliationRequestStore(JdbcTemplate jdbc, CorrectionReadReceipt verifier) { this.jdbc = jdbc; this.verifier = verifier; }

    JsonNode original(UUID id, boolean submitted) {
        String table = submitted ? "reconciliation_evidence" : "reconciliation_request";
        String column = submitted ? "submitted_receipt_id" : "opened_receipt_id";
        var rows = jdbc.queryForList("SELECT c.* FROM " + table + " r JOIN correction_command_receipt c ON c.id=r." + column
            + " WHERE r.case_id=?", Ids.bytes(id));
        if (rows.size() != 1) throw CorrectionCommand.missing();
        var receipt = verifier.verify(rows.getFirst()).receipt();
        if (!id.toString().equals(receipt.at("/businessDecision/caseId").asText())
            || !(submitted ? "RECONCILE_SUBMIT" : "RECONCILE_DISCOVER").equals(receipt.path("commandType").asText()))
            throw CorrectionCommand.corrupt();
        return receipt;
    }

    JsonNode submittedCommand(UUID id) {
        var rows = jdbc.queryForList("SELECT c.* FROM reconciliation_evidence r JOIN correction_command_receipt c"
            + " ON c.id=r.submitted_receipt_id WHERE r.case_id=?", Ids.bytes(id));
        if (rows.size() != 1) throw CorrectionCommand.missing();
        return verifier.verify(rows.getFirst()).command();
    }

    void discovered(CorrectionCommand.Write write, JsonNode decision) {
        jdbc.update("INSERT INTO reconciliation_request(case_id,order_id,opened_receipt_id) VALUES(?,?,?)",
            Ids.bytes(UUID.fromString(decision.path("caseId").asText())), Ids.bytes(write.orderId()), Ids.bytes(write.receiptId()));
    }

    void requireUndiscovered(UUID dispatch) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reconciliation r JOIN reconciliation_request q ON q.case_id=r.id"
            + " WHERE r.dispatch_id=? AND r.status<>'RESOLVED'", Integer.class, Ids.bytes(dispatch)) != 0) throw CorrectionCommand.stale();
    }

    void submitted(CorrectionCommand.Write write) {
        UUID id = write.command().uuid("caseId");
        jdbc.update("INSERT INTO reconciliation_evidence(case_id,submitted_receipt_id) VALUES(?,?)", Ids.bytes(id), Ids.bytes(write.receiptId()));
        for (var attachment : write.command().body().at("/evidence/attachments"))
            jdbc.update("INSERT INTO reconciliation_attachment_use(case_id,attachment_id) VALUES(?,?)", Ids.bytes(id),
                Ids.bytes(UUID.fromString(attachment.path("attachmentId").asText())));
    }

    void approved(CorrectionCommand.Write write, JsonNode decision) {
        jdbc.update("INSERT INTO reconciliation_posting(case_id,ledger_id,approval_receipt_id) VALUES(?,?,?)",
            Ids.bytes(write.command().uuid("caseId")), Ids.bytes(UUID.fromString(decision.path("ledgerId").asText())), Ids.bytes(write.receiptId()));
    }
}
