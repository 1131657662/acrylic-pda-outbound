package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.HandoverFormalFixtureChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * R07-1 reduced goal: a FORMAL source through the real publish/claim/submit/abort chain, then REQUEST and APPROVE
 * over the real handover command service on an isolated non-empty MySQL.
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverFormalIT {
    @Test void formalSourceRunsRequestThenApprove() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission", Integer.class)).isPositive();
        profile.migrate(jdbc, "42"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var context = E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=true")) {
            HandoverFormalFixtureChecks.verify((WebApplicationContext) context);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "FORMAL source (publish/claim/submit/abort) runs REQUEST then APPROVE\n");
    }
}
