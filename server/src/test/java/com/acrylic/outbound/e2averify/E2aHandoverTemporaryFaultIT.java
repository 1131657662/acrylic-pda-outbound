package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.E2aAndroidPreparation;
import com.acrylic.outbound.inventory.HandoverTemporaryFaultFenceChecks;
import com.acrylic.outbound.inventory.HandoverTemporaryAndroidBridge;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
public class E2aHandoverTemporaryFaultIT {
    @Test void archivedTempSourceCanBeFencedBySupervisorWithoutForgingDeviceAck() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage")); profile.migrate(jdbc, "74");
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        HandoverTemporaryAndroidBridge bridge;
        try (var app = E2aCrossSourceHttpIT.start(profile, options())) {
            E2aAndroidPreparation.write((WebApplicationContext) app, profile.attempt.resolve("private"));
            var input = profile.attempt.resolve("private/android-http-input.json");
            var contract = (com.fasterxml.jackson.databind.node.ObjectNode) com.acrylic.outbound.submissions.SubmissionJson.parse(Files.readAllBytes(input));
            contract.put("otherSourceBeforeFreeze", true);
            Files.writeString(input, com.acrylic.outbound.submissions.SubmissionJson.canonical(contract));
            E2aAndroidProcess.run(profile.attempt);
            var checks = new HandoverTemporaryFaultFenceChecks((WebApplicationContext) app, profile.attempt);
            checks.migration(profile.attempt, () -> profile.migrate(jdbc));
            checks.verify();
            bridge = checks.receiving((WebApplicationContext) app, profile.attempt);
            bridge.enableWork();
            TemporaryHandoverAndroidProcess.run(profile.attempt, "prepare"); bridge.prepare();
            TemporaryHandoverAndroidProcess.run(profile.attempt, "stage"); bridge.verify();
            TemporaryHandoverAndroidProcess.run(profile.attempt, "confirm"); bridge.verifyConfirmation();
            bridge.activate(); TemporaryHandoverAndroidProcess.run(profile.attempt, "activate"); bridge.verifyActivation();
            TemporaryHandoverAndroidProcess.run(profile.attempt, "freeze");
            bridge.verifyIntake((WebApplicationContext) app);
            TemporaryHandoverAndroidProcess.run(profile.attempt, "upload"); bridge.completeWork((WebApplicationContext) app);
        }
        try (var restarted = E2aCrossSourceHttpIT.start(profile, options())) {
            TemporaryHandoverAndroidProcess.run(profile.attempt, "success");
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "Archived TEMP source -> independent supervisor FAULT_FENCE -> Android recipient final accounting and durable success after restart. Nonempty upgrade, permission/evidence refusals, final SQL and Room rollback. Synthetic identities/development keys; no original-machine new ACK.\n");
    }

    private static String[] options() {
        return new String[]{"--outbound.handover.commands.enabled=true", "--outbound.handover.fault.enabled=true",
            "--outbound.temporary.handover-intake.enabled=true", "--outbound.temporary.commands.enabled=true",
            "--server.servlet.session.cookie.secure=false"};
    }
}
