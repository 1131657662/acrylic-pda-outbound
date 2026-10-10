package com.acrylic.outbound.handover;

import com.acrylic.outbound.recovery.RecoveryReleaseInventory;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Nonempty original child sets and empty work families must all remain represented. */
final class HandoverRecoveredReviewCoverageChecks {
    static void verify(RecoveryReleaseInventory inventory) {
        var tables=inventory.facts().path("tables");
        var expected=Map.of("inventory_import",1,"inventory_import_row",2,"inventory_snapshot",1,"inventory_snapshot_bale",2,
            "delivery_mapping",2,"temporary_preparation_part",5,"temporary_preparation_ack",1,"submitted_capture",1,"submitted_event",7,
            "barcode_sync_pending",2);
        expected.forEach((table,count)->assertThat(tables.path(table).path("rows")).as(table).hasSize(count));
        for(String table:List.of("pc_work","pc_entry","pc_exception_case","pc_exception_plan","pc_disposition","pc_submission",
                "pc_submission_entry","physical_disposition_evidence","temporary_physical_disposition","temporary_handover_completion")) {
            assertThat(tables.path(table).path("columns")).as(table).isNotEmpty(); assertThat(tables.path(table).path("rows")).as(table).isEmpty();
        }
        for(String table:List.of("operation_event","api_idempotency_request","product","device_pairing","pairing_challenge","pairing_key_guard")) {
            assertThat(tables.path(table).has("rows")).as(table).isFalse();
            assertThat(tables.path(table).path("rowsSha256").asText()).matches("[a-f0-9]{64}");
        }
        var challenges=tables.path("device_challenge"); int nonce=-1;
        for(int i=0;i<challenges.path("columns").size();i++) if(challenges.path("columns").get(i).asText().equals("nonce")) nonce=i;
        assertThat(nonce).isNotNegative();
        for(JsonNode row:challenges.path("rows")) assertThat(row.get(nonce).asText()).startsWith("sha256:");
    }
    private HandoverRecoveredReviewCoverageChecks() { }
}
