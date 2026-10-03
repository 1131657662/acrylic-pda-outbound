package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.handover.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.*;

final class HandoverOutboxChecks {
    private final HandoverRollbackFixture f;
    private final HandoverSourceCommands commands;
    private final HandoverRollbackCommands calls;
    private final Path attempt;

    HandoverOutboxChecks(ConfigurableApplicationContext app, Path attempt) {
        f = new HandoverRollbackFixture(app); this.attempt = attempt;
        var store = app.getBean(HandoverStore.class); commands = new HandoverSourceCommands(f, store);
        calls = new HandoverRollbackCommands(f, store, app.getBean(HandoverCommandService.class));
    }
    void verify() throws Exception {
        long beforeRequest = count();
        var requested = SubmissionJson.parse(calls.call(commands.request(), f.requester));
        f.caseId = UUID.fromString(requested.path("businessDecision").path("caseId").asText());
        assertThat(count()).isEqualTo(beforeRequest); f.stopped(f.caseId);
        // The real stop handler emits its own messages; approval/activation start a separate measured interval.
        long baseline = count();
        for (String kind : java.util.List.of("APPROVE", "PREPARE")) {
            var receipt = SubmissionJson.parse(calls.call(commands.command(kind), f.approver));
            assertThat(receipt.path("businessDecision").path("status").asText()).isEqualTo(kind.equals("APPROVE") ? "APPROVED" : "TARGET_PREPARED");
            assertThat(count()).isEqualTo(baseline);
        }
        var command = commands.command("ACTIVATE"); var original = calls.call(command, f.approver);
        assertThat(SubmissionJson.parse(original).path("businessDecision").path("status").asText()).isEqualTo("ACTIVATED");
        assertThat(count()).isEqualTo(baseline + 1); payload();
        assertThat(calls.call(command, f.approver)).isEqualTo(original); assertThat(count()).isEqualTo(baseline + 1);
        assertThatThrownBy(() -> calls.call(commands.command("ACTIVATE"), f.approver))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("HANDOVER_CONTEXT_CHANGED"));
        assertThat(count()).isEqualTo(baseline + 1);
        Files.writeString(attempt.resolve("outbox.txt"), "Service commands after MockMvc recovery + HTTP stop/abort.\n"
            + "REQUEST/APPROVE/PREPARE: no outbox growth. ACTIVATE: one status ASSIGNED event, pending and version unique.\n"
            + "Original replay returns exact bytes; fresh duplicate refuses HANDOVER_CONTEXT_CHANGED; neither grows outbox.\n");
    }
    private void payload() {
        var row = f.jdbc.queryForMap("SELECT event_type,entity_version,published_seq,recovery_epoch,payload "
            + "FROM order_sync_outbox WHERE order_id=? ORDER BY entity_version DESC LIMIT 1", Ids.bytes(f.order));
        var body = SubmissionJson.parse(row.get("payload").toString());
        assertThat(row.get("event_type")).isEqualTo("HANDOVER_ACTIVATED");
        assertThat(row.get("published_seq")).isNull();
        assertThat(body.path("status").asText()).isEqualTo("ASSIGNED");
        assertThat(body.path("caseId").asText()).isEqualTo(f.caseId.toString());
        assertThat(body.path("workRound").asText()).isEqualTo("2");
        assertThat(body.path("targetDeviceId").asText()).isEqualTo(f.device.toString());
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM order_sync_outbox WHERE recovery_epoch=? AND order_id=? AND entity_version=?",
            Integer.class, row.get("recovery_epoch"), Ids.bytes(f.order), row.get("entity_version"))).isEqualTo(1);
    }
    private long count() {
        return f.jdbc.queryForObject("SELECT COUNT(*) FROM order_sync_outbox WHERE order_id=?", Long.class, Ids.bytes(f.order));
    }
}