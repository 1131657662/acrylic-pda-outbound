package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.HandoverContinuationAndroidBridge;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aHandoverContinuationAndroidIT {
    @Test void androidReplaysReceivingContinuationAndSubmitsOriginalPrefix() throws Exception {
        var profile = new E2aCrossSourceProfile();
        var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34");
        E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "59");
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=true",
                "--server.servlet.session.cookie.secure=false")) {
            var bridge = new HandoverContinuationAndroidBridge((WebApplicationContext) app, profile.attempt);
            bridge.writeInput();
            E2aHandoverAndroidProcess.run(profile.attempt, "HandoverContinuationRealHttpTest");
            bridge.verifyResult();
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Generated two-device identities; real loopback HTTP and Android Room; not physical-device acceptance.");
    }
}
