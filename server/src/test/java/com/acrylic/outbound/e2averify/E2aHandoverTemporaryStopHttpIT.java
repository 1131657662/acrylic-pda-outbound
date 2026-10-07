package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.E2aAndroidPreparation;
import com.acrylic.outbound.inventory.HandoverTemporaryStopChecks;
import com.acrylic.outbound.inventory.HandoverTemporaryMigrationChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverTemporaryStopHttpIT {
    @Test void originalTemporaryDeviceRecordsVerifiedStopWithoutBorrowingFormalAssignment() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage")); profile.migrate(jdbc, "66");
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        String[] options = {"--outbound.handover.commands.enabled=true", "--server.servlet.session.cookie.secure=false"};
        HandoverTemporaryStopChecks checks;
        try (var app = E2aCrossSourceHttpIT.start(profile, options)) {
            var web = (WebApplicationContext) app;
            HandoverTemporaryMigrationChecks.verify(web, profile.attempt, () -> profile.migrate(jdbc));
            E2aAndroidPreparation.write(web, profile.attempt.resolve("private")); E2aAndroidProcess.run(profile.attempt);
            checks = new HandoverTemporaryStopChecks(web, profile.attempt); checks.verify();
        }
        try (var app = E2aCrossSourceHttpIT.start(profile, options)) { checks.restart((WebApplicationContext) app); }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Android E2A STOP -> TEMP handover REQUEST -> original HTTP ABORT -> signed stop evidence; no target activation\n");
    }
}
