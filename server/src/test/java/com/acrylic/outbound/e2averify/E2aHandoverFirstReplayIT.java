package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;

/** First delivery race on real MySQL; synthetic source/identity, no HTTP or device claim. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverFirstReplayIT {
    @Test void simultaneousFirstActivationReturnsOneOriginalReceiptToBothCallers() throws Exception {
        var profile = new E2aCrossSourceProfile();
        var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34");
        E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "52");
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        HandoverFirstReplayChecks checks;
        try (var context = start(profile)) {
            checks = new HandoverFirstReplayChecks(context, profile.attempt);
            checks.firstDeliveryRace();
        }
        try (var restarted = start(profile)) { checks.replayAfterRestart(restarted); }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Two first requests waited on the same case; both original responses equal; one activation; restart replay equal\n");
    }

    private ConfigurableApplicationContext start(E2aCrossSourceProfile profile) {
        return E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=true");
    }
}
