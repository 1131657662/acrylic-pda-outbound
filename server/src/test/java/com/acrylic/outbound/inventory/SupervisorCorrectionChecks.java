package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Migrated supervisor role reaches a signed effective correction over real Web HTTP. */
public final class SupervisorCorrectionChecks {
    private final SupervisorCorrectionFixture fixture;
    public SupervisorCorrectionChecks(WebApplicationContext context) throws Exception { fixture = new SupervisorCorrectionFixture(context); }

    public void verify(Path attempt) throws Exception {
        var maker = new SupervisorCorrectionHttp(fixture.source.operator().username());
        var checker = new SupervisorCorrectionHttp(fixture.checker.user().username());
        var before = fixture.immutableSource();
        var create = fixture.create(); byte[] created = maker.command(create, 200);
        var receipt = SupervisorCorrectionReceipt.verify(created, attempt.resolve("private/server-public.der"));
        String document = receipt.path("documentId").asText(); var approve = fixture.approve(document);
        var denials = denials(maker, checker, approve);
        byte[] approved = checker.command(approve, 200);
        var signed = SupervisorCorrectionReceipt.verify(approved, attempt.resolve("private/server-public.der"));
        assertThat(signed.at("/businessDecision/status").asText()).isEqualTo("EFFECTIVE");
        assertThat(signed.at("/actor/principalId").asText()).isEqualTo(fixture.checker.user().id().toString());
        assertThat(checker.command(approve, 200)).isEqualTo(approved);
        var changed = approve.deepCopy().put("reason", "changed request under the original key");
        assertThat(SubmissionJson.parse(checker.command(changed, 409)).path("code").asText()).isEqualTo("IDEMPOTENCY_CONTENT_MISMATCH");
        assertAccounting(document); assertThat(fixture.immutableSource()).isEqualTo(before);
        var output = SubmissionJson.object().put("orderId", fixture.order).put("documentId", document);
        output.set("before", before); output.set("after", fixture.immutableSource()); output.set("denials", denials);
        output.set("result", fixture.result()); output.set("receipt", signed);
        Files.writeString(attempt.resolve("supervisor-evidence.json"), SubmissionJson.canonical(output));
        Files.write(attempt.resolve("create-response.json"), created); Files.write(attempt.resolve("approve-response.json"), approved);
        Files.writeString(attempt.resolve("real-records.tsv"), String.join(String.valueOf((char) 9), fixture.order, document,
            fixture.checker.user().id().toString()) + System.lineSeparator());
    }

    private ObjectNode denials(SupervisorCorrectionHttp maker, SupervisorCorrectionHttp checker, ObjectNode approve) throws Exception {
        var result = SubmissionJson.object();
        var denied = SubmissionJson.parse(maker.command(approve, 403));
        assertThat(denied.path("code").asText()).isEqualTo("CORRECTION_PERMISSION_DENIED"); result.set("makerCannotApprove", denied);
        denied = SubmissionJson.parse(checker.command(fixture.create(), 403));
        assertThat(denied.path("code").asText()).isEqualTo("CORRECTION_PERMISSION_DENIED"); result.set("checkerCannotCreate", denied);
        fixture.makerApprovalRole(true);
        try {
            denied = SubmissionJson.parse(maker.command(approve, 403));
            assertThat(denied.path("code").asText()).isEqualTo("CORRECTION_SELF_APPROVAL_DENIED"); result.set("selfApproval", denied);
        } finally { fixture.makerApprovalRole(false); }
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM correction_ledger", Integer.class)).isZero();
        assertThat(fixture.result().path("version").asText()).isEqualTo("1");
        return result;
    }

    private void assertAccounting(String document) {
        var result = fixture.result(); assertThat(result.path("version").asText()).isEqualTo("2");
        assertThat(result.path("actualCount").asLong()).isEqualTo(fixture.originalCount);
        assertThat(result.path("weightG").asLong()).isEqualTo(fixture.originalWeight + 1);
        var entries = fixture.jdbc.queryForList("SELECT quantity_delta,weight_delta_g,entry_kind FROM correction_ledger");
        assertThat(entries).hasSize(1); var entry = entries.getFirst();
        assertThat(((Number) entry.get("quantity_delta")).longValue()).isZero();
        assertThat(((Number) entry.get("weight_delta_g")).longValue()).isEqualTo(1);
        assertThat(entry.get("entry_kind")).isEqualTo("WEIGHT_DIFF");
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM correction_command_receipt WHERE document_id=UUID_TO_BIN(?)",
            Integer.class, document)).isEqualTo(2);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM correction_effect WHERE order_id=UUID_TO_BIN(?)",
            Integer.class, fixture.order)).isEqualTo(1);
    }
}
