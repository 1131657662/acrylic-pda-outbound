package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;

/** Service transactions after business-handler recovery and HTTP stop/abort; generated identities, no field claim. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverFirstReplayIT {
    @Test void simultaneousFirstActivationReturnsOneOriginalReceiptToBothCallers() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "58"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        HandoverFirstReplayChecks checks;
        try (var context = start(profile)) {
            checks = new HandoverFirstReplayChecks(context, profile.attempt);
            checks.firstDeliveryRace();
        }
        try (var restarted = start(profile)) { checks.replayAfterRestart(restarted); }
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "Both first deliveries waited at identity guard; original bytes equal; one activation; restart replay equal.");
    }
    private ConfigurableApplicationContext start(E2aCrossSourceProfile profile) {
        return E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=true");
    }
}