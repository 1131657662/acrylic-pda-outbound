package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryLinkConfirmHttpChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolated non-empty MySQL + real HTTP verification of the M2b-4b chain: a linked temporary source is
 * confirmed over the running server and every effect lands on the linked formal order.
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aTemporaryLinkConfirmIT {
    @Test void linkedSourceConfirmsOverRealHttpAndEffectsLandOnFormalOrder() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission", Integer.class)).isPositive();
        profile.migrate(jdbc, "41"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var context = E2aCrossSourceHttpIT.start(profile, "--outbound.temporary.commands.enabled=true")) {
            TemporaryLinkConfirmHttpChecks.verify((WebApplicationContext) context);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "LINK then NORMAL_CONFIRM over real HTTP; reservations, accounting, slot and order events on the formal order\n");
    }
}
