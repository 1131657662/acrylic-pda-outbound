package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Full typed rows include result pointers, original facts, postings, cycle ownership, receipts and order versions. */
final class CorrectionOutboxFacts {
    private static final List<String> TABLES = List.of("outbound_order", "order_result_version", "order_current_result",
        "order_effective_scan", "order_effective_line", "submission", "submission_scope", "order_submission_slot",
        "dispatch_record", "inventory_ledger", "bale", "bale_commitment", "inventory_position", "correction_document",
        "correction_line", "correction_effect", "correction_ledger", "cycle_reopening", "correction_occupancy_restoration",
        "correction_command_receipt", "api_idempotency_request", "operation_event", "order_sync_outbox");
    private final CorrectionAccountingHttpFixture f;
    CorrectionOutboxFacts(CorrectionAccountingHttpFixture f) { this.f = f; }

    ObjectNode snapshot() {
        var result = SubmissionJson.object();
        for (String table : TABLES) {
            var rows = f.jdbc.queryForList("SELECT * FROM " + table).stream().map(this::encode).sorted().toList();
            var array = result.putArray(table); rows.forEach(array::add);
        }
        return result;
    }

    void unchanged(ObjectNode before, String stage) throws Exception {
        var after = snapshot();
        Files.writeString(f.evidence.resolve(stage + "-before.json"), before.toPrettyString(), StandardOpenOption.CREATE_NEW);
        Files.writeString(f.evidence.resolve(stage + "-after.json"), after.toPrettyString(), StandardOpenOption.CREATE_NEW);
        assertThat(after).as(stage + " business rows unchanged").isEqualTo(before);
    }

    private String encode(Map<String, Object> row) {
        var result = SubmissionJson.object();
        new TreeMap<>(row).forEach((key, value) -> {
            if (value == null) result.putNull(key);
            else result.putObject(key).put("type", value.getClass().getName()).put("value",
                value instanceof byte[] bytes ? Base64.getEncoder().encodeToString(bytes) : value.toString());
        });
        return result.toString();
    }
}
