package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.HandoverTemporaryAndroidBridge;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aHandoverTemporaryStopAndroidIT {
    @Test void receivingDeviceClosesItsOwnRoundWithCompleteActualEvidenceAndKeptOwners() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));profile.migrate(jdbc,"71");E2aCrossSourceHttpSetup.signing(profile.attempt);
        HandoverTemporaryAndroidBridge bridge;
        try(var app=E2aCrossSourceHttpIT.start(profile,E2aHandoverTemporarySubmissionAndroidIT.options(false))) {
            var web=(WebApplicationContext)app;bridge=E2aHandoverTemporarySubmissionAndroidIT.source(profile,web);
            HandoverEvidenceMigrationChecks.verify(profile,jdbc);bridge.enableWork();
            prepare(profile,bridge);bridge.stop(web,"disabled");
        }
        try(var app=E2aCrossSourceHttpIT.start(profile,E2aHandoverTemporarySubmissionAndroidIT.options(true))) {
            var web=(WebApplicationContext)app;bridge.verifyIntake(web);TemporaryHandoverAndroidProcess.run(profile.attempt,"upload");
            TemporaryHandoverAndroidProcess.run(profile.attempt,"evidence");bridge.stop(web,"before");
            TemporaryHandoverAndroidProcess.run(profile.attempt,"stop");bridge.stop(web,"after");
        }
        try(var app=E2aCrossSourceHttpIT.start(profile,E2aHandoverTemporarySubmissionAndroidIT.options(true))) {
            bridge.stop((WebApplicationContext)app,"after");
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual receiving Android E2A/STOP and isolated HTTP/MySQL, closed defaults, proof/tail fault, lost response, Room rollback and restart replay. Synthetic keys/identities.\n");
    }
    private void prepare(E2aCrossSourceProfile profile,HandoverTemporaryAndroidBridge bridge) throws Exception {
        TemporaryHandoverAndroidProcess.run(profile.attempt,"prepare");bridge.prepare();
        TemporaryHandoverAndroidProcess.run(profile.attempt,"stage");bridge.verify();
        TemporaryHandoverAndroidProcess.run(profile.attempt,"confirm");bridge.verifyConfirmation();
        bridge.activate();TemporaryHandoverAndroidProcess.run(profile.attempt,"activate");bridge.verifyActivation();
        TemporaryHandoverAndroidProcess.run(profile.attempt,"freeze");
    }
}
