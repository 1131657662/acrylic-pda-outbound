package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** The stopped original device terminalizes its submission through the existing real HTTP abort route. */
public final class HandoverStopAbortChecks {
    private final HandoverStopFixture f;
    private byte[] stop;
    private JsonNode abort;

    public HandoverStopAbortChecks(WebApplicationContext app, Path attempt) throws Exception {
        f = new HandoverStopFixture(app, attempt.resolve("stop-abort-http"));
        new HandoverOutboxVersionChecks(app, f).verify();
    }

    public void verify() throws Exception {
        outboxFailure(); stop = f.post(f.command, f.r.f.tokens, 200, null);
        var facts = new HandoverStopFacts(f, f.jdbc); var before = facts.snapshot();
        Files.writeString(f.directory.resolve("before-abort.json"), before.toPrettyString(), StandardOpenOption.CREATE_NEW);
        try { abort = abort("abort-first"); }
        finally { Files.writeString(f.directory.resolve("after-abort.json"), facts.snapshot().toPrettyString(), StandardOpenOption.CREATE_NEW); }
        assertThat(abort.path("status").asText()).isEqualTo("ABORTED");
        var after = facts.snapshot();
        for (String table : List.of("submission_scope", "order_submission_slot", "order_assignment", "offline_authorization",
                "handover_case", "handover_evidence", "handover_checklist", "handover_command_receipt", "api_idempotency_request",
                "bale_commitment", "dispatch_record", "inventory_ledger", "order_change_request", "recovery_package", "temporary_work_origin"))
            assertThat(after.path(table)).as("original evidence and reservations retained: " + table).isEqualTo(before.path(table));
        versions(f.jdbc);
        var replay = facts.snapshot(); assertThat(abort("abort-replay")).isEqualTo(abort);
        assertThat(f.post(f.command, f.r.f.tokens, 200, null)).isEqualTo(stop); facts.unchanged(replay);
    }

    private void outboxFailure() throws Exception {
        f.jdbc.execute("CREATE TRIGGER test_handover_outbox_failure BEFORE INSERT ON order_sync_outbox FOR EACH ROW BEGIN IF NEW.event_type='HANDOVER_STOPPED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected handover outbox failure'; END IF; END");
        try {
            var facts = new HandoverStopFacts(f, f.jdbc); var before = facts.snapshot();
            f.post(f.command, f.r.f.tokens, 500, null); facts.unchanged(before);
        } finally { f.jdbc.execute("DROP TRIGGER test_handover_outbox_failure"); }
    }

    private JsonNode abort(String name) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/submissions/" + f.r.f.id() + "/abort"))
            .timeout(java.time.Duration.ofSeconds(30)).header("Content-Type", "application/json")
            .header("X-Server-Recovery-Epoch", "1").header("Authorization", "Bearer " + f.r.f.tokens.path("accessToken").asText())
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(f.r.f.abortBody()))).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        Files.writeString(f.directory.resolve(name + ".json"), response.body(), StandardOpenOption.CREATE_NEW);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return SubmissionJson.parse(response.body()).path("data");
    }

    private void versions(JdbcTemplate jdbc) throws Exception {
        var rows = jdbc.queryForList("SELECT entity_version,event_type FROM order_sync_outbox WHERE order_id=UUID_TO_BIN(?) AND recovery_epoch=1 ORDER BY entity_version", f.r.f.order);
        Files.writeString(f.directory.resolve("outbox-versions.json"), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(rows), StandardOpenOption.CREATE_NEW);
        var last = rows.getLast();
        assertThat(last.get("event_type")).isEqualTo("SUBMISSION_RECALL_PENDING");
        assertThat(rows.get(rows.size() - 2).get("event_type")).isEqualTo("HANDOVER_STOPPED");
        long version = ((Number) last.get("entity_version")).longValue();
        assertThat(jdbc.queryForObject("SELECT row_version FROM outbound_order WHERE id=UUID_TO_BIN(?)", Long.class, f.r.f.order)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)", String.class, f.r.f.id())).isEqualTo("ABORTED");
        assertThat(jdbc.queryForObject("SELECT status FROM outbound_order WHERE id=UUID_TO_BIN(?)", String.class, f.r.f.order)).isEqualTo("RECALL_PENDING");
    }

    public void restart(WebApplicationContext app) throws Exception {
        var facts = new HandoverStopFacts(f, app.getBean(JdbcTemplate.class)); var before = facts.snapshot();
        assertThat(abort("abort-restart")).isEqualTo(abort);
        assertThat(f.post(f.command, f.r.f.tokens, 200, null)).isEqualTo(stop); facts.unchanged(before);
    }
}
