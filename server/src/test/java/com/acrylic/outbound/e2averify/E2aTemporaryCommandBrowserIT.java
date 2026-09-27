package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryCommandBrowserChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A3 (browser variant): isolated non-empty MySQL plus real HTTP driven by a real browser session through the
 * Playwright flow in tools/e2a-web-command-flow.mjs.
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aTemporaryCommandBrowserIT {
    @Test void browserWebSessionRunsLinkThenConfirmOnTheFormalOrder() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission", Integer.class)).isPositive();
        profile.migrate(jdbc, "41"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var context = E2aCrossSourceHttpIT.start(profile, "--outbound.temporary.commands.enabled=true")) {
            TemporaryCommandBrowserChecks.verify((WebApplicationContext) context, profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Browser web session: login -> facts -> LINK -> facts -> NORMAL_CONFIRM; effects on the formal order\n");
    }
}
