package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverTemporaryAndroidActivationIT {
    @Test void namedAndroidRecipientInstallsOriginalActivationAndRetainedPrefixAtomically() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage")); profile.migrate(jdbc);
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=true", "--server.servlet.session.cookie.secure=false")) {
            var web = (WebApplicationContext) app;
            var bridge = source(profile, web);
            TemporaryHandoverAndroidProcess.run(profile.attempt, "prepare"); bridge.prepare();
            TemporaryHandoverAndroidProcess.run(profile.attempt, "stage"); bridge.verify();
            TemporaryHandoverAndroidProcess.run(profile.attempt, "confirm"); bridge.verifyConfirmation();
            bridge.activate(); TemporaryHandoverAndroidProcess.run(profile.attempt, "activate"); bridge.verifyActivation();
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "Actual source Android STOP and receiving Android preparation, confirmation, activation installation and Room recovery.\n");
    }

    private HandoverTemporaryAndroidBridge source(E2aCrossSourceProfile profile, WebApplicationContext web) throws Exception {
        E2aAndroidPreparation.write(web, profile.attempt.resolve("private"));
        var input = profile.attempt.resolve("private/android-http-input.json");
        var contract = (com.fasterxml.jackson.databind.node.ObjectNode) SubmissionJson.parse(Files.readString(input));
        contract.put("otherSourceBeforeFreeze", true); Files.writeString(input, SubmissionJson.canonical(contract));
        E2aAndroidProcess.run(profile.attempt);
        var approval = new HandoverTemporaryApprovalChecks(web, profile.attempt); approval.verify(web);
        return new HandoverTemporaryAndroidBridge(web, approval, profile.attempt);
    }
}
