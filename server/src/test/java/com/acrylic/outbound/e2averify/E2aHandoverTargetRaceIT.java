package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.handover.HandoverCommandService;
import com.acrylic.outbound.handover.HandoverStore;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

/** A committed target-role revocation wins while activation waits at the shared identity lock. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverTargetRaceIT {
    @Test void activationReadsCommittedTargetRevocationAfterIdentityWait() throws Exception {
        var profile = new E2aCrossSourceProfile();
        var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34");
        E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "58");
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=true")) {
            var f = new HandoverRollbackFixture(app);
            var store = app.getBean(HandoverStore.class);
            var commands = new HandoverSourceCommands(f, store);
            var calls = new HandoverRollbackCommands(f, store, app.getBean(HandoverCommandService.class));
            var request = SubmissionJson.parse(calls.call(commands.request(), f.requester));
            f.stopped(UUID.fromString(request.path("businessDecision").path("caseId").asText()));
            calls.call(commands.command("APPROVE"), f.approver);
            calls.call(commands.command("PREPARE"), f.approver);
            var activate = commands.command("ACTIVATE");
            var facts = new HandoverRollbackFacts(f, jdbc);
            var before = facts.snapshot();
            try (var blocker = jdbc.getDataSource().getConnection();
                    var pool = Executors.newSingleThreadExecutor()) {
                blocker.setAutoCommit(false);
                try {
                    try (var lock = blocker.createStatement()) {
                        lock.executeQuery("SELECT id FROM user_management_guard WHERE id=1 FOR UPDATE").close();
                    }
                    var pending = pool.submit(() -> outcome(calls, activate, f));
                    awaitWaiter(jdbc, pending, profile.attempt);
                    try (var revoke = blocker.prepareStatement("DELETE FROM user_role WHERE user_id=?")) {
                        revoke.setBytes(1, Ids.bytes(f.operator));
                        assertThat(revoke.executeUpdate()).isPositive();
                    }
                    blocker.commit();
                    assertThat(pending.get(30, TimeUnit.SECONDS)).isEqualTo("HANDOVER_TARGET_USER_INVALID");
                    assertThat(facts.snapshot()).isEqualTo(before);
                } finally {
                    blocker.rollback();
                    pool.shutdownNow();
                }
            }
            jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code IN ('WAREHOUSE_ADMIN','SUPERVISOR')",
                Ids.bytes(f.operator));
            var original = calls.call(activate, f.approver);
            assertThat(SubmissionJson.parse(original).path("businessDecision").path("status").asText())
                .isEqualTo("ACTIVATED");
            assertThat(calls.call(activate, f.approver)).isEqualTo(original);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Observed real identity-row waiter; target revocation committed first, activation refused without effects, original key recovered.");
    }

    private String outcome(HandoverRollbackCommands calls,
            com.fasterxml.jackson.databind.node.ObjectNode command, HandoverRollbackFixture f) {
        try { calls.call(command, f.approver); return "UNEXPECTED_ACTIVATION"; }
        catch (BusinessException refusal) { return refusal.code(); }
    }

    private void awaitWaiter(org.springframework.jdbc.core.JdbcTemplate jdbc, Future<String> pending,
            java.nio.file.Path attempt) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            var waits = jdbc.queryForList("SELECT THREAD_ID,LOCK_STATUS,OBJECT_NAME FROM performance_schema.data_locks "
                + "WHERE OBJECT_SCHEMA=DATABASE() AND OBJECT_NAME='user_management_guard' AND LOCK_STATUS='WAITING'");
            if (!waits.isEmpty()) {
                Files.writeString(attempt.resolve("target-identity-waiters.json"),
                    new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(waits).toPrettyString());
                assertThat(pending).isNotDone();
                return;
            }
            assertThat(pending).isNotDone();
            Thread.sleep(50);
        }
        throw new AssertionError("Target activation identity waiter not observed");
    }
}
