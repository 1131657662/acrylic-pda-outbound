package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Mutates copies of actual signed approval facts, leaving retained originals untouched. */
public final class RecoveryReceivedPhysicalSemanticChecks {
    public static void verify(JsonNode original,JsonNode current,Input input) {
        check(original,current,input);
        for(var change:List.of(new Change("handover_case","physical_state","UNCHECKED"),new Change("handover_case","row_version","4"),
                new Change("handover_checklist","state","REMOVED"),new Change("handover_checklist","delivery_code","changed"),
                new Change("handover_checklist","dispatch_cycle","999"),new Change("handover_checklist","verified_at","2099-01-01 00:00:00.000"),
                new Change("bale_commitment","state","RELEASED"))) {
            ObjectNode changed=current.deepCopy(); var table=changed.path(change.table()); int index=-1;
            for(int i=0;i<table.path("columns").size();i++) if(table.path("columns").get(i).asText().equals(change.column())) index=i;
            assertThat(index).isNotNegative(); assertThat(table.path("rows")).isNotEmpty();
            ((ArrayNode)table.path("rows").get(0)).set(index,TextNode.valueOf(RecoveryReleaseRows.encoded(change.value())));
            assertThatThrownBy(()->check(original,changed,input)).isInstanceOf(RecoveryControlException.class);
        }
        ObjectNode missing=current.deepCopy(); ((ArrayNode)missing.path("handover_checklist").path("rows")).remove(0);
        assertThatThrownBy(()->check(original,missing,input)).hasMessage("RECOVERY_SCOPE_APPROVAL_CHECKLIST");
    }
    private static void check(JsonNode original,JsonNode actual,Input input) {
        var approval=new RecoveryReleaseApprovalEffects(actual,input.keys(),input.inventory().facts());
        var now=Instant.now().plusSeconds(1);
        var before=RecoveryReleaseApprovedRows.before(actual,approval,new RecoveryReleaseApprovedRows.Context(input.inventory().facts(),now));
        var receipts=new RecoveryReleaseFenceReceipts(before,input.keys(),input.inventory().facts());
        RecoveryReleaseScopeSemantics.verify(original,before,receipts,new RecoveryReleaseScopeSemantics.Context(input.inventory().facts(),now));
    }
    public record Input(RecoveryReleaseInventory inventory,RecoveryReceiptKeys keys) { }
    private record Change(String table,String column,String value) { }
    private RecoveryReceivedPhysicalSemanticChecks() { }
}
