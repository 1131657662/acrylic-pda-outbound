package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.E2aAndroidPreparation;
import com.acrylic.outbound.inventory.HandoverTemporaryApprovalChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverTemporaryApprovalHttpIT {
    @Test void independentSupervisorApprovesVerifiedTemporarySourceWithoutReleasingStock() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage")); profile.migrate(jdbc);
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        String[] options = {"--outbound.handover.commands.enabled=true", "--server.servlet.session.cookie.secure=false"};
        HandoverTemporaryApprovalChecks checks;
        try (var app = E2aCrossSourceHttpIT.start(profile, options)) {
            var web = (WebApplicationContext) app;
            E2aAndroidPreparation.write(web, profile.attempt.resolve("private"));
            var input = profile.attempt.resolve("private/android-http-input.json");
            var contract = (com.fasterxml.jackson.databind.node.ObjectNode) com.acrylic.outbound.submissions.SubmissionJson.parse(Files.readString(input));
            contract.put("otherSourceBeforeFreeze", true);
            Files.writeString(input, com.acrylic.outbound.submissions.SubmissionJson.canonical(contract));
            E2aAndroidProcess.run(profile.attempt);
            checks = new HandoverTemporaryApprovalChecks(web, profile.attempt); checks.verify(web);
        }
        try (var app = E2aCrossSourceHttpIT.start(profile, options)) { checks.restart((WebApplicationContext) app); }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Android E2A STOP -> original HTTP ABORT -> handover STOP -> independent supervisor approval; no target activation\n");
    }
}
