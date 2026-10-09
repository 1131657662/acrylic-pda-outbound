package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual final submit from Android's frozen bytes, exact last SQL rollback and unique accounting after restart. */
final class HandoverFormalFaultCompletion {
    private final HandoverFormalFaultSource f;
    private final JsonNode session;
    private final Path attempt;
    private final JsonNode body;
    private int calls;

    HandoverFormalFaultCompletion(HandoverFormalFaultSource f, JsonNode session, Path attempt) throws Exception {
        this.f = f; this.session = session; this.attempt = attempt;
        body = SubmissionJson.parse(Files.readAllBytes(attempt.resolve("android-frozen-submission.json")));
    }

    void verify() throws Exception {
        rollback();
        byte[] first = post(200); // Processed response deliberately not delivered to Android.
        byte[] second = post(200);
        assertThat(SubmissionJson.parse(first).path("data")).isEqualTo(SubmissionJson.parse(second).path("data"));
        var receipt = SubmissionJson.parse(first).path("data");
        assertThat(receipt.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(receipt.path("payloadSha256").asText()).isEqualTo(SubmissionJson.sha(body));
        Files.writeString(attempt.resolve("formal-final-receipt.json"), SubmissionJson.canonical(receipt), StandardOpenOption.CREATE_NEW);
        requireAccounting(f.source.jdbc);
        f.unchangedOriginal();
    }

    private void rollback() throws Exception {
        var jdbc = f.source.jdbc; var before = f.facts.snapshot("final-submit-before");
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(com.acrylic.outbound.common.ApiErrors.class);
        var logs = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>(); logs.start(); logger.addAppender(logs);
        jdbc.execute("CREATE TRIGGER d07_formal_submit_tail AFTER INSERT ON operation_event FOR EACH ROW BEGIN "
            + "IF NEW.event_type='SUBMISSION_COMPLETED' AND EXISTS(SELECT 1 FROM order_result_version WHERE order_id=NEW.entity_id) "
            + "AND EXISTS(SELECT 1 FROM inventory_ledger l JOIN dispatch_record d ON d.id=l.dispatch_id WHERE d.order_id=NEW.entity_id) "
            + "THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='D07_FORMAL_SUBMIT_LAST_SQL'; END IF; END");
        try { post(500); HandoverTemporaryIntakeChecks.requireFault(logs.list, "D07_FORMAL_SUBMIT_LAST_SQL"); }
        finally { jdbc.execute("DROP TRIGGER d07_formal_submit_tail"); logger.detachAppender(logs); logs.stop(); }
        assertThat(f.facts.snapshot("final-submit-after")).isEqualTo(before);
        Files.writeString(attempt.resolve("formal-final-sql.txt"), "D07_FORMAL_SUBMIT_LAST_SQL; SQLSTATE=45000; code=1644; result/ledger/owner/all durable tables rolled back\n");
    }

    void restart(JdbcTemplate jdbc) throws Exception {
        var before = HandoverFaultSnapshot.capture(jdbc);
        assertThat(SubmissionJson.parse(post(200)).path("data"))
            .isEqualTo(SubmissionJson.parse(Files.readAllBytes(attempt.resolve("formal-final-receipt.json"))));
        assertThat(HandoverFaultSnapshot.capture(jdbc)).isEqualTo(before);
        requireAccounting(jdbc);
        Files.writeString(attempt.resolve("formal-restart-accounting.json"), before.toString(), StandardOpenOption.CREATE_NEW);
    }

    private void requireAccounting(JdbcTemplate jdbc) {
        String id = body.path("submissionId").asText(); String order = body.at("/source/sourceOrderId").asText();
        assertThat(jdbc.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)", String.class, id)).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_result_version WHERE order_id=UUID_TO_BIN(?)", Long.class, order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE submission_id=UUID_TO_BIN(?)", Long.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger l JOIN dispatch_record d ON d.id=l.dispatch_id WHERE d.submission_id=UUID_TO_BIN(?)", Long.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT SUM(l.weight_delta_g) FROM inventory_ledger l JOIN dispatch_record d ON d.id=l.dispatch_id WHERE d.submission_id=UUID_TO_BIN(?)", Long.class, id)).isEqualTo(-601500);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?) AND state='DISPATCHED'", Long.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE submission_id=UUID_TO_BIN(?)", Long.class, f.source.r.f.id())).isZero();
    }

    private byte[] post(int status) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/orders/" + body.at("/source/sourceOrderId").asText() + "/submissions"))
            .timeout(java.time.Duration.ofSeconds(30)).header("Authorization", "Bearer " + session.path("accessToken").asText())
            .header("X-Server-Recovery-Epoch", "1").header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        Files.write(attempt.resolve("formal-submit-" + status + "-" + ++calls + "-" + java.util.UUID.randomUUID() + ".json"), response.body(), StandardOpenOption.CREATE_NEW);
        assertThat(response.statusCode()).as(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(status);
        return response.body();
    }
}
