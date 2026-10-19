package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.SubmissionHttpInput;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aSubmissionChunksIT {
    @Test void actualTcpChunksReassembleOnceWithP5Guards() throws Exception {
        var profile = new E2aCrossSourceProfile();
        var raw = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(raw, "34");
        E2aV34Seed.seed(raw, profile.attempt.resolve("storage"));
        profile.migrate(raw);
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        SubmissionHttpInput input;
        try (var setup = E2aCrossSourceHttpIT.start(profile)) {
            input = SubmissionHttpInput.prepare((org.springframework.web.context.WebApplicationContext) setup, true);
        }
        var p5 = new com.acrylic.outbound.recovery.RecoveryRetirementApplicationFixture(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile, p5.options())) {
            var port = ((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) app)
                .getWebServer().getPort();
            new SubmissionChunkHttp(port, profile.attempt.resolve("submission-chunks"), input).run();
        }
        p5.nextProcessStarts();
        assertThat(raw.queryForObject("SELECT COUNT(*) FROM submission_payload WHERE submission_id=UUID_TO_BIN(?)",
            Integer.class, input.submissionId())).isEqualTo(1);
        assertThat(raw.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE submission_id=UUID_TO_BIN(?)",
            Integer.class, input.submissionId())).isEqualTo(2);
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "formal TCP chunk transport and P5 guards passed");
    }
}
