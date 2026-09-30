package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.SupervisorCorrectionChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aSupervisorCorrectionHttpIT {
    @Test void separatedSupervisorSignsAndCommitsCorrectionOverRealWebHttp() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "50"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var context = E2aCrossSourceHttpIT.start(profile, "--server.servlet.session.cookie.secure=false",
                "--outbound.corrections.commands.enabled=true")) {
            new SupervisorCorrectionChecks((WebApplicationContext) context).verify(profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Migrated SUPERVISOR; actual Web HTTP; signed effective correction; denials/replay/one effect; original projections unchanged");
    }
}
