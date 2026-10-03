package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.CorrectionOutboxChecks;
import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aCorrectionOutboxIT {
    @Test void correctionsAndWithdrawalsKeepOrderOutboxAndProjectionVersionsConsistent() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        Flyway.configure().dataSource(jdbc.getDataSource()).load().migrate(); E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile, "--outbound.corrections.commands.enabled=true",
                "--server.servlet.session.cookie.secure=false")) {
            CorrectionOutboxChecks.verify((WebApplicationContext) app, profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Real separated HTTP correction/withdrawal, publisher projection version consistency, insert failure rollback and original-key recovery. Isolated fixture source and physical evidence, no field acceptance.");
    }
}
