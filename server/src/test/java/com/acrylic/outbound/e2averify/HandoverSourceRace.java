package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.assertThat;

/** Proves activation waits for the original submission transaction and reads its committed result. */
final class HandoverSourceRace {
    private final HandoverSourceChecks checks;
    private final Path evidence;

    HandoverSourceRace(HandoverSourceChecks checks, Path evidence) {
        this.checks = checks; this.evidence = evidence;
    }

    void successCommitsBeforeActivation() throws Exception {
        checks.status("ABORTED");
        var command = checks.commands.command("ACTIVATE");
        var jdbc = checks.fixture.jdbc;
        try (var blocker = jdbc.getDataSource().getConnection(); var worker = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            try {
                succeed(blocker);
                var own = new JdbcTemplate(new SingleConnectionDataSource(blocker, true));
                var expected = new HandoverRollbackFacts(checks.fixture, own).snapshot();
                var result = worker.submit(() -> outcome(command));
                awaitWaiter(result);
                blocker.commit();
                assertThat(result.get(20, TimeUnit.SECONDS)).isEqualTo("HANDOVER_ORIGINAL_RESULT_OPEN");
                var actual = checks.facts.snapshot();
                assertThat(actual).as("only the competing original-result transaction committed").isEqualTo(expected);
                Files.writeString(evidence.resolve("source-race-expected.json"), expected.toPrettyString());
                Files.writeString(evidence.resolve("source-race-actual.json"), actual.toPrettyString());
            } finally { blocker.rollback(); worker.shutdownNow(); }
        }
    }

    private void succeed(Connection connection) throws Exception {
        try (var statement = connection.prepareStatement("UPDATE submission SET status='SUCCEEDED' WHERE id=?")) {
            statement.setBytes(1, Ids.bytes(checks.fixture.submission));
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void awaitWaiter(Future<String> result) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            var waits = checks.fixture.jdbc.queryForList("SELECT ENGINE_TRANSACTION_ID,THREAD_ID,LOCK_DATA "
                + "FROM performance_schema.data_locks WHERE OBJECT_SCHEMA=DATABASE() "
                + "AND OBJECT_NAME='submission' AND LOCK_STATUS='WAITING'");
            if (waits.size() == 1) {
                Files.writeString(evidence.resolve("source-race-lock-waiter.json"),
                    new ObjectMapper().valueToTree(waits).toPrettyString());
                assertThat(result).isNotDone();
                return;
            }
            assertThat(result).as("activation must not finish before the original result commits").isNotDone();
            Thread.sleep(50);
        }
        throw new AssertionError("No real submission lock waiter observed");
    }

    private String outcome(com.fasterxml.jackson.databind.node.ObjectNode command) {
        try { checks.caller.call(command, checks.fixture.approver); return "UNEXPECTED_ACTIVATION"; }
        catch (BusinessException refusal) { return refusal.code(); }
    }
}
