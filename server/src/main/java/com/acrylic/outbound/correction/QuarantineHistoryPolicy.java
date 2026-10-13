package com.acrylic.outbound.correction;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Every prior cycle must have zero effective contribution and an original stock-restoration approval. */
final class QuarantineHistoryPolicy {
    private QuarantineHistoryPolicy() { }
    static void eligible(JsonNode facts) {
        var dispatches=facts.path("dispatches"); var ledger=facts.path("ledger");
        if(!dispatches.isArray() || !ledger.isArray()) throw CorrectionCommand.ledgerConflict();
        long cycle=facts.at("/stock/dispatchCycle").asLong();
        if(dispatches.isEmpty() && ledger.isEmpty()) { if(cycle!=1) throw CorrectionCommand.ledgerConflict();return; }
        var history=facts.path("history");
        if(!history.isArray() || history.size()!=dispatches.size() || history.size()!=cycle-1) throw CorrectionCommand.ledgerConflict();
        var actualDispatches=new HashSet<String>(); var actualLedger=new HashSet<String>();
        long next=1;
        for(var item:history) {
            validate(item,facts,next++);
            if(!actualDispatches.add(item.at("/dispatch/dispatchId").asText())) throw CorrectionCommand.ledgerConflict();
            for(var entry:item.path("ledger")) if(!actualLedger.add(entry.path("id").asText())) throw CorrectionCommand.ledgerConflict();
        }
        requireIds(dispatches,"dispatchId",actualDispatches); requireIds(ledger,"id",actualLedger);
    }
    private static void validate(JsonNode item,JsonNode facts,long cycle) {
        var dispatch=item.path("dispatch");var reopening=item.path("reopening"); var approval=item.path("stockRestorationReceipt");
        if(!"COMPLETED".equals(item.at("/result/orderStatus").asText()) || dispatch.path("count").asLong()!=0 || dispatch.path("weightG").asLong()!=0
            || !dispatch.path("baleId").equals(facts.at("/stock/baleId")) || dispatch.path("dispatchCycle").asLong()!=cycle
            || !item.at("/result/warehouseId").equals(facts.at("/stock/warehouseId"))
            || !"EFFECTIVE".equals(reopening.path("documentStatus").asText())
            || !Set.of("PHYSICAL_RETURN","DISPATCH_CANCELLATION").contains(reopening.path("type").asText())
            || reopening.path("cycleBefore").asLong()!=cycle || reopening.path("cycleAfter").asLong()!=cycle+1
            || !"RECONCILE_RETURN_APPROVE".equals(approval.path("commandType").asText())
            || !approval.path("orderId").equals(item.at("/result/orderId"))
            || !approval.at("/context/dispatchId").equals(dispatch.path("dispatchId"))
            || !item.path("reopeningSha256").isTextual()
            || !approval.at("/context/reopeningSha256").equals(item.path("reopeningSha256"))
            || !"RESOLVED".equals(approval.at("/businessDecision/status").asText())
            || !"NOT_DEDUCTED".equals(approval.at("/businessDecision/bookStatus").asText())
            || approval.at("/businessDecision/dispatchCycle").asLong()!=cycle+1) throw CorrectionCommand.ledgerConflict();
        contribution(item);
    }
    private static void contribution(JsonNode item) {
        var rows=item.path("ledger");if(!rows.isArray() || rows.isEmpty()) throw CorrectionCommand.ledgerConflict();
        var entries=new ArrayList<CorrectionContribution.Entry>();
        for(var row:rows) entries.add(new CorrectionContribution.Entry(uuid(row.path("id")),uuid(row.path("baleId")),uuid(row.path("lineId")),
            row.path("kind").asText(),row.path("quantityDelta").asLong(),row.path("weightDeltaG").asLong(),
            uuid(row.path("reversesEntryId")),uuid(row.path("reversesInventoryEntryId")),uuid(row.path("restoresEntryId")),"ORIGINAL".equals(row.path("kind").asText())));
        if(!CorrectionContribution.active(entries,uuid(item.at("/dispatch/baleId")),new long[]{0,0}).isEmpty()) throw CorrectionCommand.ledgerConflict();
    }
    private static void requireIds(JsonNode rows,String field,Set<String> expected) {
        var actual=new HashSet<String>();
        for(var row:rows) if(!row.hasNonNull(field) || !actual.add(row.path(field).asText())) throw CorrectionCommand.ledgerConflict();
        if(!actual.equals(expected)) throw CorrectionCommand.ledgerConflict();
    }
    private static UUID uuid(JsonNode value) { return value.isMissingNode() || value.isNull()?null:UUID.fromString(value.asText()); }
}
