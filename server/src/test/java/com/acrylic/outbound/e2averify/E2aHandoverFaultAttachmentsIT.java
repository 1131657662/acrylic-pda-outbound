package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.*;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
public class E2aHandoverFaultAttachmentsIT {
    @Test void actualFaultAttachmentsSurviveRecipientSuccessAndClosedWriteRestart() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage")); profile.migrate(jdbc, "75");
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile, options(true))) {
            var web = (WebApplicationContext) app;
            E2aAndroidPreparation.write(web, profile.attempt.resolve("private"));
            var input = profile.attempt.resolve("private/android-http-input.json");
            var contract = (com.fasterxml.jackson.databind.node.ObjectNode) com.acrylic.outbound.submissions.SubmissionJson.parse(Files.readAllBytes(input));
            contract.put("otherSourceBeforeFreeze", true);
            Files.writeString(input, com.acrylic.outbound.submissions.SubmissionJson.canonical(contract));
            E2aAndroidProcess.run(profile.attempt);
            var checks = new HandoverTemporaryFaultFenceChecks(web, profile.attempt);
            checks.migration(profile.attempt, () -> profile.migrate(jdbc)); checks.verifyAttachments();
            var bridge = checks.receiving(web, profile.attempt); bridge.enableWork();
            TemporaryHandoverAndroidProcess.run(profile.attempt, "prepare"); bridge.prepare();
            TemporaryHandoverAndroidProcess.run(profile.attempt, "stage"); bridge.verify();
            TemporaryHandoverAndroidProcess.run(profile.attempt, "confirm"); bridge.verifyConfirmation();
            bridge.activate(); TemporaryHandoverAndroidProcess.run(profile.attempt, "activate"); bridge.verifyActivation();
            TemporaryHandoverAndroidProcess.run(profile.attempt, "freeze"); bridge.verifyIntake(web);
            TemporaryHandoverAndroidProcess.run(profile.attempt, "upload"); bridge.completeWork(web);
        }
        try (var restarted = E2aCrossSourceHttpIT.start(profile, options(false))) {
            TemporaryHandoverAndroidProcess.run(profile.attempt, "success");
            HandoverFaultAttachmentRestartChecks.verify((WebApplicationContext) restarted, profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Actual 5x10MiB opaque attachments -> independent supervisor fault fence -> Android recipient final accounting -> restart with fault/attachment writes closed. Synthetic identities/development keys.\n");
    }

    private static String[] options(boolean writes) {
        return new String[]{"--outbound.handover.commands.enabled=true", "--outbound.handover.fault.enabled=" + writes,
            "--outbound.handover.attachments.enabled=" + writes, "--outbound.temporary.handover-intake.enabled=true",
            "--outbound.temporary.commands.enabled=true", "--server.servlet.session.cookie.secure=false"};
    }
}
