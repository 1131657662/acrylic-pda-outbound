package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.inventory.CorrectionAttachmentChecks.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Rejected CREATEs and post-write failures leave all command rows absent. */
final class CorrectionAttachmentRejections {
    private final SupervisorCorrectionFixture f;
    private final CorrectionAttachmentHttp maker;
    private final Path attempt;

    CorrectionAttachmentRejections(SupervisorCorrectionFixture f, CorrectionAttachmentHttp maker, Path attempt) {
        this.f = f; this.maker = maker; this.attempt = attempt;
    }

    void verify(JsonNode own, JsonNode foreign) throws Exception {
        var missingId = own.deepCopy(); ((ObjectNode) missingId).remove("attachmentId");
        rejected(withAttachment(f.create(), missingId), "INVALID_CORRECTION_ATTACHMENT", 400);
        var wrongSize = own.deepCopy(); ((ObjectNode) wrongSize).put("sizeBytes", "1");
        rejected(withAttachment(f.create(), wrongSize), "INVALID_CORRECTION_ATTACHMENT", 400);
        var wrongSha = own.deepCopy(); ((ObjectNode) wrongSha).put("evidenceSha256", "0".repeat(64));
        rejected(withAttachment(f.create(), wrongSha), "INVALID_CORRECTION_ATTACHMENT", 400);
        rejected(withAttachment(f.create(), foreign), "CORRECTION_ATTACHMENT_NOT_FOUND", 404);
        var missing = own.deepCopy(); ((ObjectNode) missing).put("attachmentId", UUID.randomUUID().toString());
        rejected(withAttachment(f.create(), missing), "CORRECTION_ATTACHMENT_NOT_FOUND", 404);
        var mismatch = withAttachment(f.create(), own);
        ((ObjectNode) mismatch.path("lines").get(0)).put("evidenceId", UUID.randomUUID().toString());
        rejected(mismatch, "CORRECTION_EVIDENCE_REQUIRED", 400);
        var physical = withAttachment(f.create().put("type", "PHYSICAL_RETURN"), own);
        ((ObjectNode) physical.path("lines").get(0)).remove("attachments");
        rejected(physical, "CORRECTION_EVIDENCE_REQUIRED", 400);
        var tooMany = withAttachment(f.create(), own);
        for (int i = 0; i < 5; i++) ((ObjectNode) tooMany.path("lines").get(0)).withArray("attachments").add(own.deepCopy());
        rejected(tooMany, "ATTACHMENT_LIMIT_EXCEEDED", 400);
        rollback(withAttachment(f.create(), own));
    }

    void capacity(JsonNode maximum) throws Exception {
        var body = withAttachment(f.create(), maximum); var line = body.path("lines").get(0).deepCopy();
        for (int i = 0; i < 5; i++) body.withArray("lines").add(line.deepCopy());
        rejected(body, "ATTACHMENT_LIMIT_EXCEEDED", 400);
    }

    private void rollback(ObjectNode command) throws Exception {
        f.jdbc.execute("CREATE TRIGGER attachment_test_rollback BEFORE INSERT ON correction_attachment_use"
            + " FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ATTACHMENT_TEST_ROLLBACK'");
        try { rejected(command, "INTERNAL_ERROR", 500); }
        finally { f.jdbc.execute("DROP TRIGGER attachment_test_rollback"); }
        // Same original key remains executable because the failed transaction left neither document nor receipt.
        var response = maker.command(command, 200);
        assertThat(maker.command(command, 200)).isEqualTo(response);
        Files.write(attempt.resolve("attachment-rollback-recovered-response.json"), response);
    }

    private void rejected(ObjectNode command, String expected, int status) throws Exception {
        var before = snapshot();
        code(SubmissionJson.parse(maker.command(command, status)), expected);
        assertThat(snapshot()).as("all correction/receipt/reference/idempotency/audit bytes unchanged").isEqualTo(before);
        Files.writeString(attempt.resolve("attachment-denials.log"), expected + " unchanged=true\n",
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private Map<String, List<String>> snapshot() {
        var result = new TreeMap<String, List<String>>();
        for (String table : List.of("correction_document", "correction_line", "correction_command_receipt",
                "correction_attachment_use", "api_idempotency_request", "operation_event", "correction_effect", "correction_ledger")) {
            var rows = f.jdbc.queryForList("SELECT * FROM " + table).stream().map(row -> {
                var encoded = new TreeMap<String, String>();
                row.forEach((key, value) -> encoded.put(key, value instanceof byte[] bytes
                    ? "bytes:" + Base64.getEncoder().encodeToString(bytes) : value == null ? "null" : value.getClass().getName() + ":" + value));
                return encoded.toString();
            }).sorted().toList();
            result.put(table, rows);
        }
        return result;
    }
}
