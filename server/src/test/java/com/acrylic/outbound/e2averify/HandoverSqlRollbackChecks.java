package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.handover.HandoverCommandService;
import com.acrylic.outbound.handover.HandoverStore;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** One real activation transaction, exact rollback, then the original command's durable recovery. */
final class HandoverSqlRollbackChecks {
    private final HandoverRollbackFixture fixture;
    private final HandoverRollbackCommands commands;
    private final HandoverRollbackFacts facts;
    private final JdbcTemplate jdbc;
    private final Path attempt;
    private final ObjectNode command;
    private ObjectNode before, committed;
    private byte[] original;

    HandoverSqlRollbackChecks(ConfigurableApplicationContext context, Path attempt) {
        this.attempt = attempt;
        jdbc = context.getBean(JdbcTemplate.class);
        fixture = new HandoverRollbackFixture(jdbc);
        commands = commands(context);
        commands.prepare();
        command = commands.activate();
        facts = new HandoverRollbackFacts(fixture, jdbc);
    }

    void failureRollsBack() throws Exception {
        before = facts.snapshot();
        write("before", before);
        write("before-summary", facts.summary());
        assertThat(facts.summary().path("caseStatus").asText()).isEqualTo("TARGET_PREPARED");
        assertThat(facts.summary().path("retained").asLong()).isEqualTo(1);
        try (var fault = new HandoverSqlRollbackFault(jdbc, fixture, attempt)) {
            var failure = catchThrowable(() -> commands.call(command, fixture.approver));
            var sql = HandoverSqlRollbackFault.requireExpectedFailure(failure);
            write("fault", SubmissionJson.object().put("sqlState", sql.getSQLState())
                .put("errorCode", sql.getErrorCode()).put("message", sql.getMessage()));
        }
        var after = facts.snapshot();
        write("after-rollback", after);
        assertThat(after).as("sixteen typed table snapshots after real written effects were rolled back").isEqualTo(before);
    }

    void originalKeyRecovers() throws Exception {
        original = commands.call(command, fixture.approver);
        committed = facts.snapshot();
        assertActivated(facts.summary());
        assertThat(commands.call(command, fixture.approver)).isEqualTo(original);
        assertThat(facts.snapshot()).isEqualTo(committed);
        assertThat(committed.path("bale_commitment")).as("nonempty original commitment bytes retained")
            .isEqualTo(before.path("bale_commitment"));
        write("committed", committed);
        write("committed-summary", facts.summary());
        Files.write(attempt.resolve("activation-original.json"), original);
        Files.writeString(attempt.resolve("activation-command.json"), SubmissionJson.canonical(command));
    }

    void replayAfterRestart(ConfigurableApplicationContext context) throws Exception {
        assertThat(commands(context).call(command, fixture.approver)).isEqualTo(original);
        var restarted = new HandoverRollbackFacts(fixture, context.getBean(JdbcTemplate.class));
        var snapshot = restarted.snapshot();
        write("restarted", snapshot);
        assertThat(snapshot).isEqualTo(committed);
        write("restarted-summary", restarted.summary());
    }

    private static void assertActivated(ObjectNode summary) {
        assertThat(summary.path("caseStatus").asText()).isEqualTo("ACTIVATED");
        for (String field : java.util.List.of("activeAssignments", "oldRevoked", "outbox", "pendingOutbox",
                "activationReceipts", "activationAudits", "retained"))
            assertThat(summary.path(field).asLong()).as(field).isEqualTo(1);
        assertThat(summary.path("scopeRound").asLong()).isEqualTo(2);
        assertThat(summary.path("slotRound").asLong()).isEqualTo(2);
    }

    private HandoverRollbackCommands commands(ConfigurableApplicationContext context) {
        return new HandoverRollbackCommands(fixture, context.getBean(HandoverStore.class),
            context.getBean(HandoverCommandService.class));
    }

    private void write(String name, ObjectNode value) throws Exception {
        Files.writeString(attempt.resolve("rollback-" + name + ".json"), value.toString());
    }
}
