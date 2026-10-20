package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.SubmissionHttpInput;
import com.acrylic.outbound.recovery.RecoveryRetirementApplicationFixture;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Set;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Real TCP fault probes for the formal chunk finalizer; all identities and inputs are synthetic. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aSubmissionChunkFaultIT {
    @Test void finalizeFaultsAreSerializedRolledBackAndFenced() throws Exception {
        var profile = new E2aCrossSourceProfile(); var raw = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(raw, "34"); E2aV34Seed.seed(raw, profile.attempt.resolve("storage")); profile.migrate(raw);
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        SubmissionHttpInput[] inputs;
        try (var setup = E2aCrossSourceHttpIT.start(profile)) {
            var web = (WebApplicationContext) setup;
            inputs = new SubmissionHttpInput[]{SubmissionHttpInput.prepare(web, false),
                SubmissionHttpInput.prepare(web, false), SubmissionHttpInput.prepare(web, false)};
        }
        var p5 = new RecoveryRetirementApplicationFixture(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile, p5.options())) {
            var port = ((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) app).getWebServer().getPort();
            concurrentFinalize(profile, raw, port, inputs[0]);
            rollbackAtAuditTail(profile, raw, port, inputs[1]);
            rejectAfterAuthRevocation(profile, raw, port, inputs[2]);
        }
        p5.nextProcessStarts();
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "TCP concurrent finalize lock wait, audit-tail rollback, and lock-wait auth revocation passed");
    }

    private void concurrentFinalize(E2aCrossSourceProfile profile, JdbcTemplate raw, int port,
            SubmissionHttpInput input) throws Exception {
        var wire = new SubmissionChunkFaultWire(profile.attempt.resolve("concurrent"), port, input);
        wire.upload(); var locks = new SubmissionChunkNativeLocks(raw);
        try (var blocker = connection(profile)) {
            blocker.setAutoCommit(false); locks.holdIdentity(blocker);
            try (var pool = Executors.newFixedThreadPool(2)) {
                var first = pool.submit(() -> wire.finish(200));
                var second = pool.submit(() -> wire.finish(200));
                try { wire.save("native-lock-wait", locks.waiting("user_management_guard", 2)); }
                finally { blocker.commit(); }
                JsonNode a = first.get(60, TimeUnit.SECONDS), b = second.get(60, TimeUnit.SECONDS);
                assertThat(a).isEqualTo(b); new SubmissionChunkFaultState(raw, profile.attempt.resolve("storage")).succeeded(wire, a);
            }
        }
    }

    private void rollbackAtAuditTail(E2aCrossSourceProfile profile, JdbcTemplate raw, int port,
            SubmissionHttpInput input) throws Exception {
        var wire = new SubmissionChunkFaultWire(profile.attempt.resolve("rollback"), port, input);
        wire.upload(); var state = new SubmissionChunkFaultState(raw, profile.attempt.resolve("storage"));
        var database = state.database(Set.of()); var files = state.files();
        wire.save("database-before", database); wire.save("files-before", files);
        String trigger = "e2a_chunk_tail_" + java.util.UUID.randomUUID().toString().replace("-", "");
        raw.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON operation_event FOR EACH ROW BEGIN "
            + "IF NEW.event_type='SUBMISSION_COMPLETED' THEN SIGNAL SQLSTATE '42000' SET MESSAGE_TEXT='isolated chunk tail'; END IF; END");
        try {
            wire.finish(500);
            var afterDatabase = state.database(Set.of()); var afterFiles = state.files();
            wire.save("database-after", afterDatabase); wire.save("files-after", afterFiles);
            assertThat(afterDatabase).isEqualTo(database); assertThat(afterFiles).isEqualTo(files);
        }
        finally { raw.execute("DROP TRIGGER " + trigger); }
        state.succeeded(wire, wire.finish(200));
    }

    private void rejectAfterAuthRevocation(E2aCrossSourceProfile profile, JdbcTemplate raw, int port,
            SubmissionHttpInput input) throws Exception {
        var wire = new SubmissionChunkFaultWire(profile.attempt.resolve("revocation"), port, input);
        wire.upload(); var locks = new SubmissionChunkNativeLocks(raw); String actor = locks.actor(wire);
        long original = locks.authVersion(actor); var state = new SubmissionChunkFaultState(raw, profile.attempt.resolve("storage"));
        var database = state.database(Set.of()); var files = state.files();
        wire.save("database-before", database); wire.save("files-before", files);
        try (var blocker = connection(profile)) {
            blocker.setAutoCommit(false); locks.hold(blocker, "app_user", actor);
            try (var pool = Executors.newSingleThreadExecutor()) {
                var future = pool.submit(() -> wire.finish(401));
                try {
                    wire.save("native-lock-wait", locks.waiting("app_user", 1));
                    locks.bumpAuthVersion(blocker, actor, original + 1); blocker.commit();
                } finally { blocker.rollback(); }
                assertThat(future.get(60, TimeUnit.SECONDS).path("code").asText()).isEqualTo("AUTH_EXPIRED");
            }
        }
        raw.update("UPDATE app_user SET auth_version=? WHERE id=?", original, com.acrylic.outbound.common.Ids.bytes(java.util.UUID.fromString(actor)));
        var afterDatabase = state.database(Set.of()); var afterFiles = state.files();
        wire.save("database-after", afterDatabase); wire.save("files-after", afterFiles);
        assertThat(afterDatabase).isEqualTo(database); assertThat(afterFiles).isEqualTo(files);
    }

    private static Connection connection(E2aCrossSourceProfile profile) throws Exception {
        return DriverManager.getConnection(profile.url, profile.user, System.getenv("E2A_CROSS_PASSWORD"));
    }
}
