package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.HandoverTemporaryAndroidBridge;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aHandoverTemporaryChunksAndroidIT {
    @Test void actualLargeAndroidFreezeResumesChunksAcrossServerRestart() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));profile.migrate(jdbc);
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        HandoverTemporaryAndroidBridge bridge;
        try(var app=E2aCrossSourceHttpIT.start(profile,E2aHandoverTemporarySubmissionAndroidIT.options(false))) {
            var web=(WebApplicationContext)app;bridge=E2aHandoverTemporarySubmissionAndroidIT.source(profile,web);bridge.enableLargeWork();
            prepareAndFreeze(profile,bridge);bridge.chunks(web,"disabled");
        }
        try(var app=E2aCrossSourceHttpIT.start(profile,E2aHandoverTemporarySubmissionAndroidIT.options(true))) {
            bridge.chunks((WebApplicationContext)app,"begin");
        }
        try(var app=E2aCrossSourceHttpIT.start(profile,E2aHandoverTemporarySubmissionAndroidIT.options(true))) {
            var web=(WebApplicationContext)app;bridge.chunks(web,"finish");
            TemporaryHandoverAndroidProcess.run(profile.attempt,"upload");bridge.chunks(web,"accepted");bridge.completeWork(web);
        }
        try(var app=E2aCrossSourceHttpIT.start(profile,E2aHandoverTemporarySubmissionAndroidIT.options(true))) {
            TemporaryHandoverAndroidProcess.run(profile.attempt,"success");
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Actual large Android TEMP handover body, durable chunks and server restart; closed routing, faults and independent success. Synthetic identities/keys.\n");
    }
    private void prepareAndFreeze(E2aCrossSourceProfile profile,HandoverTemporaryAndroidBridge bridge) throws Exception {
        TemporaryHandoverAndroidProcess.run(profile.attempt,"prepare");bridge.prepare();
        TemporaryHandoverAndroidProcess.run(profile.attempt,"stage");bridge.verify();
        TemporaryHandoverAndroidProcess.run(profile.attempt,"confirm");bridge.verifyConfirmation();
        bridge.activate();TemporaryHandoverAndroidProcess.run(profile.attempt,"activate");bridge.verifyActivation();
        TemporaryHandoverAndroidProcess.run(profile.attempt,"freeze");
    }
}
