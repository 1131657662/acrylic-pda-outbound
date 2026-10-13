package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads old dispatches and the actual approvals that returned each cycle to stock. */
@Component
final class QuarantineHistoryFacts {
    private final JdbcTemplate jdbc;
    private final ReconciliationFacts facts;
    private final ReturnedStockFacts returns;
    private final CorrectionReadReceipt receipts;
    QuarantineHistoryFacts(JdbcTemplate jdbc,ReconciliationFacts facts,ReturnedStockFacts returns,CorrectionReadReceipt receipts) {
        this.jdbc=jdbc;this.facts=facts;this.returns=returns;this.receipts=receipts;
    }
    ArrayNode read(UUID bale) {
        var result=SubmissionJson.object().putArray("history");
        var ids=jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM dispatch_record WHERE bale_id=? ORDER BY dispatch_cycle,id",String.class,Ids.bytes(bale));
        for(String id:ids) {
            UUID dispatch=UUID.fromString(id); var item=facts.read(dispatch);
            var reopening=(com.fasterxml.jackson.databind.node.ObjectNode)returns.read(dispatch);
            var approval=restoration(dispatch,reopening.path("reopeningId").asText());
            if(!approval.at("/context/facts/reopening").equals(reopening)) throw CorrectionCommand.ledgerConflict();
            String reopeningHash=ReconciliationFacts.hash(reopening);
            item.put("reopeningSha256",reopeningHash);
            item.put("returnReceiptSha256",CorrectionCommand.sha(CorrectionReceiptCodec.bytes(reopening.path("approvalReceipt"))));
            item.put("stockRestorationReceiptSha256",CorrectionCommand.sha(CorrectionReceiptCodec.bytes(approval)));
            reopening.set("approvalReceipt",summary(reopening.path("approvalReceipt")));
            var restored=summary(approval);var context=restored.putObject("context");
            context.set("dispatchId",approval.at("/context/dispatchId"));context.put("reopeningSha256",reopeningHash);
            restored.set("businessDecision",approval.path("businessDecision"));
            item.set("reopening",reopening);item.set("stockRestorationReceipt",restored);
            result.add(item);
        }
        return result;
    }
    private com.fasterxml.jackson.databind.node.ObjectNode summary(com.fasterxml.jackson.databind.JsonNode receipt) {
        var result=SubmissionJson.object();
        for(String field:java.util.List.of("receiptId","commandId","commandType","orderId","actor","recordedAt")) result.set(field,receipt.path(field));
        return result;
    }
    byte[] original(com.fasterxml.jackson.databind.JsonNode item,String kind) {
        String pointer=switch(kind) {case "return" -> "/reopening/approvalReceipt/receiptId";case "restoration" -> "/stockRestorationReceipt/receiptId";
            default -> throw CorrectionCommand.missing();};
        var rows=jdbc.queryForList("SELECT * FROM correction_command_receipt WHERE id=UUID_TO_BIN(?)",item.at(pointer).asText());
        if(rows.size()!=1) throw CorrectionCommand.corrupt();
        var bytes=receipts.verify(rows.getFirst()).bytes();
        String hash=kind.equals("return")?"returnReceiptSha256":"stockRestorationReceiptSha256";
        if(!CorrectionCommand.sha(bytes).equals(item.path(hash).asText())) throw CorrectionCommand.corrupt();
        return bytes;
    }
    private com.fasterxml.jackson.databind.JsonNode restoration(UUID dispatch,String reopening) {
        var rows=jdbc.queryForList("""
            SELECT c.* FROM returned_stock_release r JOIN correction_command_receipt c ON c.id=r.approval_receipt_id
            WHERE r.original_dispatch_id=? AND r.cycle_reopening_id=UUID_TO_BIN(?)
            """,Ids.bytes(dispatch),reopening);
        if(rows.size()!=1) throw CorrectionCommand.ledgerConflict();
        return receipts.verify(rows.getFirst()).receipt();
    }
}
