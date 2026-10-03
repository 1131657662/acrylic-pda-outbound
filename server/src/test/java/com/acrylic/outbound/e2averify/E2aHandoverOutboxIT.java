package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Service-level outbox transaction checks following actual business recovery and HTTP stop/abort. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverOutboxIT {
    @Test void activationRecordsOneRecoverableNotificationAndReplayAddsNone() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "58"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=true")) {
            new HandoverOutboxChecks(app, profile.attempt).verify();
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "ACTIVATE records one HANDOVER_ACTIVATED/status ASSIGNED message; request/approve/prepare/replay/refusal add none\n");
    }
}