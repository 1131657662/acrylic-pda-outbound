package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

final class TemporaryRoundConfirmationState {
    private final TemporaryRoundConfirmationHttpFixture f;
    TemporaryRoundConfirmationState(TemporaryRoundConfirmationHttpFixture f) { this.f=f; }
    String snapshot() {
        var j=f.http.database.jdbc;var state=object();
        for(String table:List.of("submission_scope","submission","order_submission_slot","outbound_order","bale_commitment",
            "inventory_position","dispatch_record","inventory_ledger","order_result_version","order_effective_line",
            "temporary_source_continuation","temporary_source_continuation_closure","temporary_decision_state",
            "temporary_command_receipt","temporary_decision_event","api_idempotency_request","order_sync_outbox","operation_event"))
            state.put(table,j.queryForList("SELECT * FROM "+table).stream().map(row->{
                var values=object();row.forEach((key,value)->values.put(key,value instanceof byte[] bytes
                    ?java.util.HexFormat.of().formatHex(bytes):String.valueOf(value)));return canonical(values);
            }).sorted().toList().toString());
        return canonical(state);
    }
    void completed(JsonNode receipt,int count) {
        var j=f.http.database.jdbc;String id=text(f.body,"submissionId");
        var success=receipt.path("businessDecision").path("submissionReceipt");
        assertThat(success.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(success.path("source")).isEqualTo(f.body.path("source"));
        assertThat(success.path("localDeletionAllowed").asBoolean(true)).isFalse();
        assertThat(success.path("actualCount").asInt()).isEqualTo(count);
        assertThat(success.path("inventoryDeducted").asBoolean()).isEqualTo(f.body.path("autoDeductInventory").asBoolean());
        assertThat(j.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE submission_id=UUID_TO_BIN(?)",Integer.class,id)).isEqualTo(count);
        assertThat(j.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isZero();
        assertThat(j.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?) AND state='DISPATCHED'",Integer.class,id)).isEqualTo(count);
        assertThat(j.queryForObject("SELECT COUNT(*) FROM order_result_version WHERE submission_id=UUID_TO_BIN(?) AND actual_count=? AND inventory_deducted=false",Integer.class,id,count)).isEqualTo(1);
        assertThat(j.queryForObject("SELECT status FROM temporary_source_continuation",String.class)).isEqualTo("COMPLETED");
        assertThat(j.queryForObject("SELECT receipt_text FROM temporary_source_continuation_closure WHERE target_submission_id=UUID_TO_BIN(?)",String.class,id)).isEqualTo(canonical(success));
        assertThat(j.queryForObject("SELECT BIN_TO_UUID(closure_receipt_id) FROM temporary_source_continuation_closure",String.class)).isEqualTo(text(receipt,"receiptId"));
        assertThat(j.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)",String.class,text(f.original,"submissionId"))).isEqualTo("ABORTED");
        assertThat(j.queryForObject("SELECT BIN_TO_UUID(client_order_id) FROM submission_scope WHERE id=UUID_TO_BIN(?)",String.class,f.http.database.seed.get("mappedScopeId")))
            .isEqualTo(f.original.path("source").path("clientOrderId").asText());
        assertThat(j.queryForObject("SELECT BIN_TO_UUID(authorization_id) FROM temporary_work_origin WHERE client_order_id=UUID_TO_BIN(?)",String.class,
            f.original.path("source").path("clientOrderId").asText())).isEqualTo(text(f.original,"authorizationId"));
    }
}
