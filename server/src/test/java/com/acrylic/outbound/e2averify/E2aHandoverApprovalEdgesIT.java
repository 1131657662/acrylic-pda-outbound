package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.HandoverApprovalEdgeChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverApprovalEdgesIT {
    @Test void authenticEmptySetAndUntrustedProofsHaveDifferentOutcomes() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc); E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=true", "--server.servlet.session.cookie.secure=false")) {
            new HandoverApprovalEdgeChecks((WebApplicationContext) app, profile.attempt, 0, "empty-approval").empty();
            new HandoverApprovalEdgeChecks((WebApplicationContext) app, profile.attempt, 1, "fake-proof").fakeProof();
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "Authentic empty effective-set HTTP chain succeeds, preserving highwater; absent/unsigned stop proof pair refused; PREPARE rechecks baseline.");
    }
}
