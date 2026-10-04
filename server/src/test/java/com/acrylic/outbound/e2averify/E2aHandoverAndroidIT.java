package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.HandoverApprovalWorkflowChecks;
import com.acrylic.outbound.inventory.HandoverAndroidBridge;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aHandoverAndroidIT {
    @Test void signedServerHandoverImportsIntoAndroidAndReplaysLostAcknowledgement() throws Exception {
        var profile = new E2aCrossSourceProfile();
        var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34");
        E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "58");
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        String[] options = {"--outbound.handover.commands.enabled=true", "--server.servlet.session.cookie.secure=false"};
        try (var app = E2aCrossSourceHttpIT.start(profile, options)) {
            var workflow = new HandoverApprovalWorkflowChecks((WebApplicationContext) app, profile.attempt, true);
            workflow.verify();
            var bridge = new HandoverAndroidBridge(workflow, profile.attempt);
            bridge.writeInput();
            E2aHandoverAndroidProcess.run(profile.attempt);
            bridge.verifyResult();
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Same-instance real HTTP to Android production verifiers/import/ack; generated identities and JVM Room only.");
    }
}
