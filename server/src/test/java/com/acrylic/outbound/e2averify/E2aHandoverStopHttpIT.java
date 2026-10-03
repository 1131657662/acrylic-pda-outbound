package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.handover.HandoverStopController;
import com.acrylic.outbound.inventory.HandoverStopChecks;
import com.acrylic.outbound.inventory.HandoverStopExistingChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aHandoverStopHttpIT {
    @Test void durableOriginalRecoveryStopsOnceAndHttpRetriesRecoverSignedOriginal() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "58"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile)) { assertThat(app.getBeansOfType(HandoverStopController.class)).isEmpty(); }
        String[] options = {"--outbound.handover.commands.enabled=true", "--server.servlet.session.cookie.secure=false"};
        HandoverStopChecks checks; HandoverStopExistingChecks existing;
        try (var app = E2aCrossSourceHttpIT.start(profile, options)) {
            assertThat(app.getBeansOfType(HandoverStopController.class)).hasSize(1);
            checks = new HandoverStopChecks((WebApplicationContext) app, profile.attempt); checks.verify();
            existing = new HandoverStopExistingChecks((WebApplicationContext) app, profile.attempt); existing.verify();
        }
        try (var app = E2aCrossSourceHttpIT.start(profile, options)) {
            checks.restart((WebApplicationContext) app); existing.restart((WebApplicationContext) app);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Real HTTP stop: identity/binding refusals, missing file, rollback, two first-key waiters, original/restart, retained commitments; authentic prior recall, signature corruption, cross-case key conflict. Generated EC fixture identities, no physical-device claim.");
    }
}