package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.handover.*;
import java.nio.file.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

/** A real identity revocation commits while the first REQUEST waits at the common identity prefix. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverIdentityRaceIT {
    @Test void revocationDuringWriteWaitIsRecheckedBeforeAnyCommandWrite() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "58"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=true")) {
            var f = new HandoverRollbackFixture(app); var store = app.getBean(HandoverStore.class);
            var command = new HandoverSourceCommands(f, store).request();
            var calls = new HandoverRollbackCommands(f, store, app.getBean(HandoverCommandService.class));
            var facts = new HandoverRollbackFacts(f, jdbc); var before = facts.snapshot();
            try (var connection = jdbc.getDataSource().getConnection(); var pool = Executors.newSingleThreadExecutor()) {
                connection.setAutoCommit(false);
                try (var lock = connection.createStatement()) { lock.executeQuery("SELECT id FROM user_management_guard WHERE id=1 FOR UPDATE").close(); }
                var pending = pool.submit(() -> { try { calls.call(command, f.requester); return "OK"; } catch (BusinessException e) { return e.code(); } });
                awaitWaiter(jdbc, pending, profile.attempt);
                try (var update = connection.prepareStatement("UPDATE app_user SET auth_version=auth_version+1 WHERE id=?")) {
                    update.setBytes(1, Ids.bytes(f.requester.id())); assertThat(update.executeUpdate()).isEqualTo(1);
                }
                connection.commit(); assertThat(pending.get(30, TimeUnit.SECONDS)).isEqualTo("SESSION_REVOKED");
            }
            var after = facts.snapshot(); assertThat(after).isEqualTo(before);
            Files.writeString(profile.attempt.resolve("identity-before.json"), before.toPrettyString());
            Files.writeString(profile.attempt.resolve("identity-after.json"), after.toPrettyString());
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "Real identity-guard waiter observed; auth-version revocation commits first; stale actor refused; sixteen business tables unchanged.");
    }
    private void awaitWaiter(org.springframework.jdbc.core.JdbcTemplate jdbc, Future<String> pending, Path attempt) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            var waits = jdbc.queryForList("SELECT THREAD_ID,LOCK_STATUS,OBJECT_NAME FROM performance_schema.data_locks "
                + "WHERE OBJECT_SCHEMA=DATABASE() AND OBJECT_NAME='user_management_guard' AND LOCK_STATUS='WAITING'");
            if (!waits.isEmpty()) {
                Files.writeString(attempt.resolve("identity-waiters.json"), new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(waits).toPrettyString()); return;
            }
            assertThat(pending.isDone()).isFalse(); Thread.sleep(50);
        }
        throw new AssertionError("Identity waiter not observed");
    }
}