package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryCommandWebHttpChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A3: isolated non-empty MySQL plus real HTTP with a Web session. It proves the A1 facts surface is sufficient to
 * build both command bodies (LINK then NORMAL_CONFIRM) and that the effects land on the linked formal order.
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aTemporaryCommandWebHttpIT {
    @Test void webSessionFactsAndCommandsLandOnTheLinkedFormalOrder() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission", Integer.class)).isPositive();
        profile.migrate(jdbc, "41"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var context = E2aCrossSourceHttpIT.start(profile, "--outbound.temporary.commands.enabled=true")) {
            TemporaryCommandWebHttpChecks.verify((WebApplicationContext) context);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Web session login -> facts -> LINK -> facts -> NORMAL_CONFIRM; effects on the linked formal order\n");
    }
}
