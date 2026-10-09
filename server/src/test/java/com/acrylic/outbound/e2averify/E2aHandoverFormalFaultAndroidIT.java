package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.HandoverFormalFaultAndroidBridge;
import com.acrylic.outbound.inventory.HandoverFormalFaultMigrationChecks;
import com.acrylic.outbound.inventory.E2aAndroidPreparation;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aHandoverFormalFaultAndroidIT {
    @Test void supervisorFaultArchiveImportsOnNamedAndroidWithoutSourceAcknowledgement() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "76");
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        HandoverFormalFaultAndroidBridge bridge;
        try (var app = E2aCrossSourceHttpIT.start(profile, options())) {
            E2aAndroidPreparation.write((WebApplicationContext) app, profile.attempt.resolve("private"));
            E2aAndroidProcess.run(profile.attempt);
            HandoverFormalFaultMigrationChecks.verify((WebApplicationContext) app, profile.attempt, () -> profile.migrate(jdbc));
            preserveSource(profile.attempt);
            bridge = new HandoverFormalFaultAndroidBridge((WebApplicationContext) app, profile.attempt);
            bridge.writeInput();
            E2aHandoverAndroidProcess.run(profile.attempt, "HandoverContinuationRealHttpTest");
            bridge.verifyResult();
            bridge.completeWork();
        }
        try (var restarted = E2aCrossSourceHttpIT.start(profile, options())) {
            bridge.restart((WebApplicationContext) restarted);
            E2aFormalFaultSuccessProcess.run(profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "FORMAL supervisor fault/actual Web/Android frozen body/final accounting/restarted success; nonempty TEMP migration and exact SQL/Room rollback. Source fixture MockMvc, synthetic identities/development keys; no original-machine new ACK.");
    }

    private static void preserveSource(java.nio.file.Path attempt) throws Exception {
        for (String name : new String[]{"android-result.json", "android-result.xml", "android-process-pid.txt"})
            Files.move(attempt.resolve(name), attempt.resolve("source-" + name));
    }

    private static String[] options() {
        return new String[]{"--outbound.handover.commands.enabled=true", "--outbound.handover.fault.enabled=true",
            "--outbound.handover.attachments.enabled=true",
            "--server.servlet.session.cookie.secure=false"};
    }
}
