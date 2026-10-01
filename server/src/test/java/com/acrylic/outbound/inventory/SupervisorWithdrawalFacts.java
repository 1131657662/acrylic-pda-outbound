package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Read-only accounting projections for the two phases of a weight-correction withdrawal. */
final class SupervisorWithdrawalFacts {
    private final SupervisorCorrectionFixture fixture;
    SupervisorWithdrawalFacts(SupervisorCorrectionFixture fixture) { this.fixture = fixture; }

    ArrayNode ledger() {
        var rows = SubmissionJson.object().putArray("rows");
        fixture.jdbc.queryForList("SELECT JSON_OBJECT('id',BIN_TO_UUID(id),'dispatch',BIN_TO_UUID(dispatch_id),"
            + "'line',BIN_TO_UUID(correction_line_id),'kind',entry_kind,'quantity',CAST(quantity_delta AS CHAR),"
            + "'weightG',CAST(weight_delta_g AS CHAR),'restores',BIN_TO_UUID(restores_entry_id),'businessKey',business_key)"
            + " FROM correction_ledger ORDER BY id", String.class).forEach(row -> rows.add(SubmissionJson.parse(row)));
        return rows;
    }

    ObjectNode state(String document) {
        var result = SubmissionJson.object(); result.set("result", fixture.result()); result.set("ledger", ledger());
        result.set("document", SubmissionJson.parse(fixture.jdbc.queryForObject(
            "SELECT JSON_OBJECT('status',status,'version',CAST(row_version AS CHAR),'maker',BIN_TO_UUID(requested_by),"
                + "'approver',BIN_TO_UUID(approved_by),'withdrawer',BIN_TO_UUID(withdrawal_requested_by),"
                + "'confirmer',BIN_TO_UUID(withdrawal_confirmed_by)) FROM correction_document WHERE id=UUID_TO_BIN(?)",
            String.class, document)));
        result.put("receipts", fixture.jdbc.queryForObject("SELECT COUNT(*) FROM correction_command_receipt", Integer.class));
        result.put("effects", fixture.jdbc.queryForObject("SELECT COUNT(*) FROM correction_effect", Integer.class));
        return result;
    }

    void assertReversal(ArrayNode original, String document) {
        var all = ledger(); assertThat(original.size()).isEqualTo(1); assertThat(all.size()).isEqualTo(2);
        var forward = original.get(0); int originals = 0, reversals = 0;
        for (var row : all) {
            if (row.path("restores").isNull()) { assertThat(row).isEqualTo(forward); originals++; continue; }
            reversals++; assertThat(row.path("restores").asText()).isEqualTo(forward.path("id").asText());
            assertThat(row.path("kind").asText()).isEqualTo("WITHDRAWAL_RESTORE");
            assertThat(row.path("dispatch")).isEqualTo(forward.path("dispatch"));
            assertThat(row.path("line")).isEqualTo(forward.path("line"));
            assertThat(row.path("quantity").asLong()).isEqualTo(-forward.path("quantity").asLong());
            assertThat(row.path("weightG").asLong()).isEqualTo(-forward.path("weightG").asLong());
            assertThat(row.path("businessKey").asText()).isEqualTo("WD:" + forward.path("line").asText() + ":" + forward.path("id").asText());
        }
        assertThat(originals).isEqualTo(1); assertThat(reversals).isEqualTo(1);
        var state = state(document); assertThat(state.path("receipts").asInt()).isEqualTo(4);
        assertThat(state.path("effects").asInt()).isEqualTo(1);
        assertThat(state.at("/document/status").asText()).isEqualTo("WITHDRAWN");
        assertThat(state.at("/document/withdrawer").asText()).isEqualTo(fixture.checker.user().id().toString());
        assertThat(state.at("/document/confirmer").asText()).isEqualTo(fixture.source.operator().id().toString());
        assertThat(fixture.result().path("version").asText()).isEqualTo("3");
        assertThat(fixture.result().path("actualCount").asLong()).isEqualTo(fixture.originalCount);
        assertThat(fixture.result().path("weightG").asLong()).isEqualTo(fixture.originalWeight);
    }
}
