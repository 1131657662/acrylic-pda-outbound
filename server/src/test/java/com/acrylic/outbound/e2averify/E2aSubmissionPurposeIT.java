package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.SubmissionPurposeChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aSubmissionPurposeIT {
    @Test void importOnlyCredentialsCannotSubmitOrBeApprovedForAccounting() throws Exception {
        var profile = new E2aCrossSourceProfile();
        var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34");
        E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "58");
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile)) {
            new SubmissionPurposeChecks((WebApplicationContext) app, profile.attempt).verify();
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Generated signed scope refusals through MockMvc and real MySQL; normal submission and approval replay preserved.");
    }
}
