package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.SubmissionHttpInput;
import com.acrylic.outbound.recovery.RecoveryRetirementApplicationFixture;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Multi-chunk evidence remains immutable and retryable when the final audit SQL rolls back. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aSubmissionChunkLargeRollbackIT {
    @Test void auditTailFailureRetainsOriginalMultiChunkEvidenceWithoutCommittingBusinessRows() throws Exception {
        var profile = new E2aCrossSourceProfile(); var raw = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(raw, "34"); E2aV34Seed.seed(raw, profile.attempt.resolve("storage")); profile.migrate(raw);
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        SubmissionHttpInput input;
        try (var setup = E2aCrossSourceHttpIT.start(profile)) {
            input = SubmissionHttpInput.prepare((WebApplicationContext) setup, true);
        }
        assertThat(input.payload().length).isGreaterThan(1048576);
        var p5 = new RecoveryRetirementApplicationFixture(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile, p5.options())) {
            var port = ((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) app).getWebServer().getPort();
            var wire = new SubmissionChunkFaultWire(profile.attempt.resolve("large-rollback"), port, input);
            assertThat(wire.count()).isGreaterThan(1); wire.upload();
            var state = new SubmissionChunkFaultState(raw, profile.attempt.resolve("storage"));
            failAtAuditTail(wire, state); retryOriginal(wire, state);
        }
        p5.nextProcessStarts();
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "Multi-chunk audit-tail rollback, original evidence retention and same-ID retry passed");
    }

    private void failAtAuditTail(SubmissionChunkFaultWire wire, SubmissionChunkFaultState state) throws Exception {
        var database = state.database(Set.of()); var files = state.files();
        String hash = SubmissionJson.sha(wire.input.body()), key = "submissions/" + hash + ".json";
        assertThat(files.has(key)).isFalse();
        wire.save("database-before", database); wire.save("files-before", files);
        String trigger = "e2a_large_tail_" + java.util.UUID.randomUUID().toString().replace("-", "");
        state.raw.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON operation_event FOR EACH ROW BEGIN "
            + "IF NEW.event_type='SUBMISSION_COMPLETED' THEN SIGNAL SQLSTATE '42000' SET MESSAGE_TEXT='isolated large tail'; END IF; END");
        try {
            assertThat(wire.finish(500).path("code").asText()).isEqualTo("INTERNAL_ERROR");
            var afterDatabase = state.database(Set.of()); var afterFiles = state.files();
            wire.save("database-after-failure", afterDatabase); wire.save("files-after-failure", afterFiles);
            assertThat(afterDatabase).isEqualTo(database);
            assertThat(afterFiles).isEqualTo(files.deepCopy().put(key, hash));
            assertThat(state.count("submission_payload", wire.input.submissionId())).isZero();
            assertThat(state.count("dispatch_record", wire.input.submissionId())).isZero();
            assertThat(state.count("submission_chunk", wire.input.submissionId())).isEqualTo(wire.count());
        } finally { state.raw.execute("DROP TRIGGER " + trigger); }
    }

    private void retryOriginal(SubmissionChunkFaultWire wire, SubmissionChunkFaultState state) throws Exception {
        var files = state.files(); var first = wire.finish(200);
        state.succeeded(wire, first);
        assertThat(wire.finish(200)).isEqualTo(first); state.succeeded(wire, first);
        assertThat(state.files()).isEqualTo(files);
        wire.save("database-after-retry", state.database(Set.of()));
        wire.save("files-after-retry", state.files());
        wire.save("success-receipt", first);
    }
}
