package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Semantic mutations use copies of the actual retained snapshots, never replace the original evidence. */
public final class RecoveryReceivedScopeSemanticChecks {
    public static int verify(JsonNode original,JsonNode actual,RecoveryReleaseInventory inventory,RecoveryReceiptKeys keys) {
        var receipts=new RecoveryReleaseFenceReceipts(actual,keys,inventory.facts());
        var context=new RecoveryReleaseScopeSemantics.Context(inventory.facts(),Instant.now().plusSeconds(1));
        RecoveryReleaseScopeSemantics.verify(original,actual,receipts,context);
        var changes=List.of(new Change("submission","status","SUCCEEDED"),new Change("bale_commitment","state","RELEASED"),
            new Change("device","row_version","999"),new Change("pda_session","access_expires_at","2099-01-01 00:00:00.000"),
            new Change("handover_case","physical_state","VERIFIED"),new Change("order_sync_outbox","published_seq","1"),
            new Change("operation_event","details","{}"),new Change("api_idempotency_request","request_sha256","f".repeat(64)),
            new Change("inventory_import_row","error_detail","tampered"));
        for(var change:changes) {
            ObjectNode broken=actual.deepCopy(); replace(broken,change);
            assertThatThrownBy(()->RecoveryReleaseScopeSemantics.verify(original,broken,receipts,context)).isInstanceOf(RecoveryControlException.class);
        }
        ObjectNode extra=actual.deepCopy(); var rows=(ArrayNode)extra.path("device").path("rows"); rows.add(rows.get(0).deepCopy());
        assertThatThrownBy(()->RecoveryReleaseScopeSemantics.verify(original,extra,receipts,context)).hasMessage("RECOVERY_SCOPE_CHANGED_COUNT_device");
        ObjectNode unknown=(ObjectNode)inventory.facts(); var device=(ObjectNode)unknown.path("tables").path("device");
        ((ArrayNode)device.path("rows")).add(device.path("rows").get(0).deepCopy()); device.put("rowCount","2");
        assertThatThrownBy(()->RecoveryReleaseScopeSemantics.verify(original,actual,receipts,
            new RecoveryReleaseScopeSemantics.Context(unknown,context.observedAt()))).hasMessage("RECOVERY_SCOPE_UNRECONCILED_device");
        ObjectNode missing=actual.deepCopy(); ((ObjectNode)missing.path("pc_work")).remove("rows");
        assertThatThrownBy(()->RecoveryReleaseScopeSemantics.verify(original,missing,receipts,context)).hasMessage("RECOVERY_SCOPE_TABLE_SHAPE");
        return changes.size()+3;
    }
    private static void replace(ObjectNode tables,Change change) {
        var table=tables.path(change.table()); int column=-1;
        for(int i=0;i<table.path("columns").size();i++) if(table.path("columns").get(i).asText().equals(change.column())) column=i;
        assertThat(column).isNotNegative(); var rows=(ArrayNode)table.path("rows"); assertThat(rows).isNotEmpty();
        int row=Set.of("operation_event","api_idempotency_request","order_sync_outbox").contains(change.table())?rows.size()-1:0;
        ((ArrayNode)rows.get(row)).set(column,TextNode.valueOf(RecoveryReleaseRows.encoded(change.value())));
    }
    private record Change(String table,String column,String value) { }
    private RecoveryReceivedScopeSemanticChecks() { }
}
