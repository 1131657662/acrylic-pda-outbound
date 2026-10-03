package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.handover.HandoverCommandService;
import com.acrylic.outbound.handover.HandoverStore;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real transaction refusals retain every row; synthetic stop evidence is not a device acceptance claim. */
final class HandoverSourceChecks {
    final HandoverRollbackFixture fixture;
    final HandoverRollbackCommands caller;
    final HandoverSourceCommands commands;
    final HandoverRollbackFacts facts;
    private final Path evidence;
    private int snapshots;

    HandoverSourceChecks(ConfigurableApplicationContext app, Path evidence) {
        this.evidence = evidence;
        fixture = new HandoverRollbackFixture(app.getBean(JdbcTemplate.class));
        var store = app.getBean(HandoverStore.class);
        caller = new HandoverRollbackCommands(fixture, store, app.getBean(HandoverCommandService.class));
        commands = new HandoverSourceCommands(fixture, store);
        facts = new HandoverRollbackFacts(fixture, fixture.jdbc);
    }

    void request() {
        byte[] receipt = caller.call(commands.request(), fixture.requester);
        fixture.stopped(UUID.fromString(SubmissionJson.parse(receipt).path("businessDecision").path("caseId").asText()));
    }

    void rejectsApprovalForNonterminalOriginal() throws Exception {
        var command = commands.command("APPROVE");
        for (String state : List.of("RECEIVING", "REVIEW_REQUIRED", "SUCCEEDED")) {
            status(state);
            rejected(command, "HANDOVER_ORIGINAL_RESULT_OPEN");
        }
        status("REJECTED");
        rejected(command, "HANDOVER_CONTEXT_CHANGED");
        status("ABORTED");
    }

    void rejectsStaleSource(String kind) throws Exception {
        var command = commands.command(kind);
        change("UPDATE submission_scope SET work_round=2 WHERE id=?", fixture.scope);
        rejected(command, "HANDOVER_CONTEXT_CHANGED");
        change("UPDATE submission_scope SET work_round=1 WHERE id=?", fixture.scope);
        change("UPDATE submission_scope SET mapping_version=mapping_version+1 WHERE id=?", fixture.scope);
        rejected(command, "HANDOVER_CONTEXT_CHANGED");
        change("UPDATE submission_scope SET mapping_version=mapping_version-1 WHERE id=?", fixture.scope);
        change("UPDATE submission_scope SET current_submission_id=NULL WHERE id=?", fixture.scope);
        rejected(command, "HANDOVER_CONTEXT_CHANGED");
        fixture.jdbc.update("UPDATE submission_scope SET current_submission_id=? WHERE id=?",
            Ids.bytes(fixture.submission), Ids.bytes(fixture.scope));
        change("UPDATE order_submission_slot SET phase='CONTINUING',current_submission_id=NULL WHERE order_id=?", fixture.order);
        rejected(command, "HANDOVER_CONTEXT_CHANGED");
        fixture.jdbc.update("UPDATE order_submission_slot SET phase='FROZEN',current_submission_id=? WHERE order_id=?",
            Ids.bytes(fixture.submission), Ids.bytes(fixture.order));
    }

    void approve() { caller.call(commands.command("APPROVE"), fixture.approver); }

    void prepare() throws Exception {
        status("RECEIVING");
        rejected(commands.command("PREPARE"), "HANDOVER_ORIGINAL_RESULT_OPEN");
        status("REJECTED");
        rejected(commands.command("PREPARE"), "HANDOVER_CONTEXT_CHANGED");
        status("ABORTED");
        caller.call(commands.command("PREPARE"), fixture.approver);
    }

    void rejected(ObjectNode command, String code) throws Exception {
        var before = facts.snapshot();
        int number = ++snapshots;
        Files.writeString(evidence.resolve("source-refusal-" + number + "-before.json"), before.toPrettyString());
        assertThatThrownBy(() -> caller.call(command, fixture.approver)).isInstanceOfSatisfying(
            BusinessException.class, error -> assertThat(error.code()).isEqualTo(code));
        var after = facts.snapshot();
        Files.writeString(evidence.resolve("source-refusal-" + number + "-after.json"), after.toPrettyString());
        assertThat(after).as("refused command has no business effects").isEqualTo(before);
    }

    byte[] activateAndReplay() throws Exception {
        status("ABORTED");
        var command = commands.command("ACTIVATE");
        byte[] receipt = caller.call(command, fixture.approver);
        var before = facts.snapshot();
        assertThat(caller.call(command, fixture.approver)).isEqualTo(receipt);
        assertThat(facts.snapshot()).isEqualTo(before);
        assertThat(facts.summary().path("scopeRound").asLong()).isEqualTo(2);
        assertThat(facts.summary().path("activeAssignments").asLong()).isEqualTo(1);
        assertThat(facts.summary().path("retained").asLong()).isEqualTo(1);
        activationVersion();
        Files.writeString(evidence.resolve("source-activation-command.json"), SubmissionJson.canonical(command));
        Files.write(evidence.resolve("source-activation-receipt.json"), receipt);
        return receipt;
    }

    private void activationVersion() throws Exception {
        var row = fixture.jdbc.queryForMap("SELECT entity_version,event_type FROM order_sync_outbox WHERE order_id=? AND recovery_epoch=1 ORDER BY entity_version DESC LIMIT 1", Ids.bytes(fixture.order));
        long version = fixture.jdbc.queryForObject("SELECT row_version FROM outbound_order WHERE id=?", Long.class, Ids.bytes(fixture.order));
        assertThat(row.get("event_type")).isEqualTo("HANDOVER_ACTIVATED");
        assertThat(((Number) row.get("entity_version")).longValue()).isEqualTo(version);
        Files.writeString(evidence.resolve("source-activation-version.json"),
            SubmissionJson.object().put("orderVersion", version).put("outboxVersion", ((Number) row.get("entity_version")).longValue()).toString());
    }

    void status(String value) {
        assertThat(fixture.jdbc.update("UPDATE submission SET status=? WHERE id=?", value, Ids.bytes(fixture.submission)))
            .isEqualTo(1);
    }

    private void change(String sql, UUID id) { assertThat(fixture.jdbc.update(sql, Ids.bytes(id))).isEqualTo(1); }
}
