package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.concurrent.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual HTTP business driver, retained original facts, manual debit and subsequent real-contribution reversal. */
public final class ReconciliationMissingPostingChecks {
    public static void verify(WebApplicationContext context, Path attempt) throws Exception {
        positive(context, attempt); new ReconciliationMissingPostingRefusals(context, attempt).verify();
        new ReconciliationMissingPostingBoundaries(context, attempt).verify();
    }

    private static void positive(WebApplicationContext context, Path attempt) throws Exception {
        var f = new ReconciliationMissingPostingFixture(context, attempt, "positive"); var source = f.original;
        var originalBytes = source.original(); var originalResult = source.result(); var originalStock = f.stock();
        source.approve(1, source.originalDispatchWeight + 25);
        f.discover(); f.submit();
        assertThat(f.checker.read(source.warehouse, f.attachment.path("attachmentId").asText(), true, 200))
            .containsExactly(Files.readAllBytes(f.directory.resolve("physical-original.bin")));
        var command = f.command("RECONCILE_APPROVE", true); var before = source.result();
        byte[] approved = concurrent(f, command);
        assertThat(f.checker.command(command, 200)).containsExactly(approved);
        assertThat(source.postings()).isEqualTo(1);
        assertThat(source.correctionWeight()).isEqualTo(-(source.originalDispatchWeight + 25));
        assertThat(source.result()).isEqualTo(before); assertThat(source.original()).isEqualTo(originalBytes);
        assertThat(f.stock().path("cycle")).isEqualTo(originalStock.path("cycle"));
        assertThat(f.stock().path("blockedReason")).isEqualTo(originalStock.path("blockedReason"));
        assertThat(f.stock().path("upstreamPresent")).isEqualTo(originalStock.path("upstreamPresent"));
        assertThat(f.stock().path("bookStatus").asText()).isEqualTo("DEDUCTED");
        assertThat(f.state().path("caseStatus").asText()).isEqualTo("RESOLVED");
        reverseContribution(f, originalResult, originalBytes);
        assertThat(source.jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_posting WHERE case_id=UUID_TO_BIN(?)", Integer.class, f.caseId)).isEqualTo(1);
        Files.writeString(f.directory.resolve("completed.marker"), "Actual HTTP missing debit, controlled evidence, independent approval, concurrent original-key replay, current-weight debit then difference and full reversal; original result/bytes preserved.");
    }

    private static void reverseContribution(ReconciliationMissingPostingFixture f,
        com.fasterxml.jackson.databind.JsonNode originalResult, com.fasterxml.jackson.databind.JsonNode originalBytes) throws Exception {
        var source = f.original;
        source.approve(1, source.originalDispatchWeight + 40);
        assertThat(source.correctionWeight()).isEqualTo(-(source.originalDispatchWeight + 40));
        var returned = source.physicalReturn(0); var line = (ObjectNode) returned.path("lines").get(0);
        line.put("evidenceId", f.attachment.path("attachmentId").asText()).put("evidenceSha256", f.attachment.path("evidenceSha256").asText());
        line.putArray("attachments").add(f.attachment);
        String document = source.signed(source.maker, returned).path("documentId").asText();
        source.signed(source.checker, source.command(document, "APPROVE"));
        assertThat(source.correctionWeight()).isZero();
        assertThat(source.result().path("count").asLong()).isEqualTo(originalResult.path("count").asLong() - 1);
        assertThat(source.original()).isEqualTo(originalBytes);
    }

    private static byte[] concurrent(ReconciliationMissingPostingFixture f, ObjectNode command) throws Exception {
        f.checker.get("/auth/csrf", 200);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> f.checker.command(command, 200));
            var second = pool.submit(() -> f.checker.command(command, 200));
            byte[] receipt = first.get(40, TimeUnit.SECONDS);
            assertThat(second.get(40, TimeUnit.SECONDS)).containsExactly(receipt);
            SupervisorCorrectionReceipt.verify(receipt, f.original.publicKey);
            Files.writeString(f.directory.resolve("approval-command.json"), SubmissionJson.canonical(command));
            Files.write(f.directory.resolve("approval-receipt.json"), receipt); return receipt;
        }
    }
}
