package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.handover.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Transaction boundaries and exact original-key recovery assertions for one prepared source. */
final class HandoverRollbackChecks {
    private final HandoverRollbackFixture fixture;
    private final HandoverRollbackCommands commands;
    private final HandoverRollbackFacts facts;
    private final HandoverAuditFaultConfiguration.FaultAuditWriter fault;
    private final Path attempt;
    private final ObjectNode command;
    private ObjectNode committed;
    private byte[] original;

    HandoverRollbackChecks(ConfigurableApplicationContext context, Path attempt) {
        this.attempt = attempt;
        var jdbc = context.getBean(JdbcTemplate.class);
        fixture = new HandoverRollbackFixture(jdbc);
        commands = commands(context); commands.prepare();
        command = commands.activate();
        facts = new HandoverRollbackFacts(fixture, jdbc);
        fault = context.getBean(HandoverAuditFaultConfiguration.HandoverAuditFaultPostProcessor.class).writer();
    }

    void failureRollsBack() throws Exception {
        var baseline = facts.snapshot();
        write("before", baseline); write("before-summary", facts.summary());
        assertThat(facts.summary().path("caseStatus").asText()).isEqualTo("TARGET_PREPARED");
        assertThat(facts.summary().path("retained").asLong()).isEqualTo(1);
        fault.arm(facts);
        assertThatThrownBy(() -> commands.call(command, fixture.approver))
            .isExactlyInstanceOf(HandoverAuditFaultConfiguration.InjectedAfterAudit.class);
        write("inside-before-abort", fault.raw); write("inside-summary", fault.observed);
        assertThat(fault.hits).isEqualTo(1);
        assertThat(fault.transactionActive).isTrue();
        assertActivated(fault.observed);
        var after = facts.snapshot(); write("after-rollback", after);
        assertThat(after).as("all written rows and retained reservations equal the pre-transaction state").isEqualTo(baseline);
        fault.disarm();
    }

    void originalKeyRecovers() throws Exception {
        original = commands.call(command, fixture.approver);
        committed = facts.snapshot();
        assertActivated(facts.summary());
        assertThat(commands.call(command, fixture.approver)).isEqualTo(original);
        assertThat(facts.snapshot()).isEqualTo(committed);
        write("committed", committed); write("committed-summary", facts.summary());
        assertThat(committed.path("bale_commitment")).isEqualTo(fault.raw.path("bale_commitment"));
        Files.write(attempt.resolve("activation-original.json"), original);
        Files.writeString(attempt.resolve("activation-command.json"), SubmissionJson.canonical(command));
    }

    void replayAfterRestart(ConfigurableApplicationContext context) throws Exception {
        assertThat(commands(context).call(command, fixture.approver)).isEqualTo(original);
        var restartedFacts = new HandoverRollbackFacts(fixture, context.getBean(JdbcTemplate.class));
        assertThat(restartedFacts.snapshot()).isEqualTo(committed);
        write("restarted-summary", restartedFacts.summary());
    }

    private void assertActivated(ObjectNode summary) {
        assertThat(summary.path("caseStatus").asText()).isEqualTo("ACTIVATED");
        for (String field : java.util.List.of("activeAssignments", "oldRevoked", "outbox", "pendingOutbox", "activationReceipts", "activationAudits", "retained"))
            assertThat(summary.path(field).asLong()).as(field).isEqualTo(1);
        assertThat(summary.path("scopeRound").asLong()).isEqualTo(2);
        assertThat(summary.path("slotRound").asLong()).isEqualTo(2);
    }

    private HandoverRollbackCommands commands(ConfigurableApplicationContext context) {
        return new HandoverRollbackCommands(fixture, context.getBean(HandoverStore.class), context.getBean(HandoverCommandService.class));
    }

    private void write(String name, ObjectNode data) throws Exception {
        Files.writeString(attempt.resolve("rollback-" + name + ".json"), SubmissionJson.canonical(data));
    }
}
