package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.ReverseCandidateVectors;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

/** Fresh isolated MySQL and actual HTTP reverse-candidate query; success setup uses the real service. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aReverseCandidateVectorsIT {
    @Test void successfulTemporarySourceRemainsVisibleToIndependentFormalCandidateConsumer() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "50"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        // The isolated listener is HTTP; permit its test session cookie without changing production defaults.
        try (var context = E2aCrossSourceHttpIT.start(profile, "--server.servlet.session.cookie.secure=false")) {
            new ReverseCandidateVectors((WebApplicationContext) context).write(profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Successful TEMP; actual formal-order HTTP query; raw relation vector; foreign warehouse denial; business facts unchanged\n");
    }
}
