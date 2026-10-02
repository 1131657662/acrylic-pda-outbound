package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryPhysicalDispositionWebHttpChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aPhysicalDispositionWebHttpIT {
    @Test void webSessionPhysicalDispositionReleasesRejectedReservation() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "56"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var context = E2aCrossSourceHttpIT.start(profile, "--outbound.temporary.commands.enabled=true")) {
            TemporaryPhysicalDispositionWebHttpChecks.verify((org.springframework.web.context.WebApplicationContext) context);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "V56 physical release real Web HTTP command passed\n");
    }
}
