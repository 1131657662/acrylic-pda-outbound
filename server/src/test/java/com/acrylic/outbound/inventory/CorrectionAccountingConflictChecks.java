package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Deliberate isolated ledger mismatches and same-value ABA must roll back without receipts or result changes. */
final class CorrectionAccountingConflictChecks {
    private final WebApplicationContext context;
    private final Path attempt;
    CorrectionAccountingConflictChecks(WebApplicationContext context, Path attempt) { this.context = context; this.attempt = attempt; }

    void verify() throws Exception { mismatch(); exhausted(); aba(); disjoint(); manual(); }

    private void mismatch() throws Exception {
        var f = new CorrectionAccountingHttpFixture(context, attempt, true, "mismatch");
        String document = f.signed(f.maker, f.create(f.dispatch, 1, f.originalDispatchWeight + 10)).path("documentId").asText();
        insert(f, "WEIGHT_DIFF", 0, -1);
        var body = f.command(document, "APPROVE"); var before = state(f, document);
        denied(f, f.checker, body);
        assertThat(state(f, document)).isEqualTo(before);
    }

    private void exhausted() throws Exception {
        var f = new CorrectionAccountingHttpFixture(context, attempt, true, "exhausted");
        String document = f.signed(f.maker, f.create(f.dispatch, 1, f.originalDispatchWeight + 10)).path("documentId").asText();
        f.jdbc.update("INSERT INTO correction_ledger(id,dispatch_id,bale_id,entry_kind,quantity_delta,weight_delta_g,"
            + "reverses_inventory_entry_id,business_key,created_at) SELECT UUID_TO_BIN(?),dispatch_id,bale_id,'REVERSAL',"
            + "-quantity_delta,-weight_delta_g,id,?,UTC_TIMESTAMP(3) FROM inventory_ledger WHERE dispatch_id=UUID_TO_BIN(?)",
            UUID.randomUUID().toString(), "isolated:" + UUID.randomUUID(), f.dispatch);
        var body = f.command(document, "APPROVE"); var before = state(f, document);
        denied(f, f.checker, body); assertThat(state(f, document)).isEqualTo(before);
    }

    private void aba() throws Exception {
        var f = new CorrectionAccountingHttpFixture(context, attempt, false, "aba"); long weight = f.originalDispatchWeight;
        String first = f.approve(1, weight + 10); f.approve(1, weight + 20); f.approve(1, weight + 10);
        f.signed(f.checker, f.command(first, "WITHDRAW"));
        var body = f.command(first, "WITHDRAW_CONFIRM"); var before = state(f, first);
        denied(f, f.maker, body); assertThat(state(f, first)).isEqualTo(before);
    }

    private void disjoint() throws Exception {
        var f = new CorrectionAccountingHttpFixture(context, attempt, true, "disjoint");
        String first = f.approve(1, f.originalDispatchWeight + 10), other = f.dispatches().get(1);
        String second = f.approve(other, 1, f.facts(other).path("weightG").asLong() + 5);
        f.withdraw(first); assertThat(f.facts(f.dispatch).path("weightG").asLong()).isEqualTo(f.originalDispatchWeight);
        f.withdraw(second); assertThat(f.result().path("weightG").asLong()).isEqualTo(f.originalWeight);
        assertThat(f.correctionWeight()).isZero();
        Files.writeString(f.evidence.resolve("completed.json"), f.result().toPrettyString());
    }

    private void manual() throws Exception {
        var f = new CorrectionAccountingHttpFixture(context, attempt, false, "manual");
        insert(f, "MANUAL_DEDUCT", -1, -f.originalDispatchWeight);
        String document = f.approve(1, f.originalDispatchWeight + 20);
        assertThat(f.correctionWeight()).isEqualTo(-f.originalDispatchWeight - 20);
        f.withdraw(document); assertThat(f.correctionWeight()).isEqualTo(-f.originalDispatchWeight);
        Files.writeString(f.evidence.resolve("completed.json"), f.result().toPrettyString());
    }

    private void insert(CorrectionAccountingHttpFixture f, String kind, long quantity, long weight) {
        f.jdbc.update("INSERT INTO correction_ledger(id,dispatch_id,bale_id,entry_kind,quantity_delta,weight_delta_g,business_key,created_at)"
            + " VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,?,UTC_TIMESTAMP(3))", UUID.randomUUID().toString(),
            f.dispatch, f.facts(f.dispatch).path("baleId").asText(), kind, quantity, weight, "isolated:" + UUID.randomUUID());
    }

    private void denied(CorrectionAccountingHttpFixture f, SupervisorCorrectionHttp actor, ObjectNode body) throws Exception {
        byte[] bytes = actor.command(body, 409); Files.write(f.evidence.resolve(body.path("commandId").asText() + "-denied.json"), bytes);
        assertThat(SubmissionJson.parse(bytes).path("code").asText()).isEqualTo("CORRECTION_LEDGER_CONFLICT");
    }

    private JsonNode state(CorrectionAccountingHttpFixture f, String document) {
        var result = SubmissionJson.object(); result.set("result", f.result()); result.put("weight", f.correctionWeight());
        result.put("postings", f.postings());
        result.put("document", f.jdbc.queryForObject("SELECT status FROM correction_document WHERE id=UUID_TO_BIN(?)", String.class, document));
        for (String table : new String[]{"correction_command_receipt", "correction_effect", "order_sync_outbox", "order_result_version"})
            result.put(table, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE order_id=UUID_TO_BIN(?)", Integer.class, f.order));
        return result;
    }
}
