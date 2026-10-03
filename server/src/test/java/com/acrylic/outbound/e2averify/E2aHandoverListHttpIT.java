package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Read-list fixture regression without a browser: HTTP pagination and ownership assertions remain unchanged. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverListHttpIT {
    @Test void syntheticHistoryStillExercisesHttpReadIsolation() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "58"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=true", "--server.servlet.session.cookie.secure=false")) {
            new HandoverListChecks(new HandoverListFixture(app, profile.attempt)).verify();
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "HTTP history pagination/ownership/no-business-write checks; synthetic history only, no successful approval or browser claim.");
    }
}