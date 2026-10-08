package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.*;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aHandoverTemporaryMultihopAndroidIT {
    @Test void thirdDeviceSubmitsCumulativeHistoryAfterActualSecondDeviceStop() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));profile.migrate(jdbc);
        E2aCrossSourceHttpSetup.signing(profile.attempt);HandoverTemporaryAndroidBridge bridge;
        try(var app=E2aCrossSourceHttpIT.start(profile,E2aHandoverTemporarySubmissionAndroidIT.options(false))) {
            bridge=source(profile,(WebApplicationContext)app);bridge.enableShortWork();
            prepare(profile,bridge);bridge.intakeDisabled();
        }
        try(var app=E2aCrossSourceHttpIT.start(profile,E2aHandoverTemporarySubmissionAndroidIT.options(true))) {
            var web=(WebApplicationContext)app;bridge.verifyIntake(web);TemporaryHandoverAndroidProcess.run(profile.attempt,"upload");
            TemporaryHandoverAndroidProcess.run(profile.attempt,"evidence");bridge.stop(web,"before");
            TemporaryHandoverAndroidProcess.run(profile.attempt,"stop");bridge.stop(web,"after");
            bridge=HandoverTemporaryNextHop.receiving(web,profile.attempt);bridge.enableFinalWork();prepare(profile,bridge);
            bridge.verifyIntake(web);TemporaryHandoverAndroidProcess.run(profile.attempt,"upload");bridge.completeWork(web);
        }
        try(var app=E2aCrossSourceHttpIT.start(profile,E2aHandoverTemporarySubmissionAndroidIT.options(true))) {
            TemporaryHandoverAndroidProcess.run(profile.attempt,"success");
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual A -> B -> C Android, complete independent STOP and supervisor approval per hop; cumulative original bytes, local counters, input faults, outbox/SQL/Room rollback, lost response, server restart and final accounting. Synthetic identities/keys.\n");
    }
    private HandoverTemporaryAndroidBridge source(E2aCrossSourceProfile profile,WebApplicationContext web) throws Exception {
        E2aAndroidPreparation.write(web,profile.attempt.resolve("private"),true);
        var input=profile.attempt.resolve("private/android-http-input.json");
        var contract=(com.fasterxml.jackson.databind.node.ObjectNode)com.acrylic.outbound.submissions.SubmissionJson.parse(Files.readAllBytes(input));
        contract.put("otherSourceBeforeFreeze",true).put("plannedCount","2");
        Files.writeString(input,com.acrylic.outbound.submissions.SubmissionJson.canonical(contract));
        E2aAndroidProcess.run(profile.attempt);
        var approval=new HandoverTemporaryApprovalChecks(web,profile.attempt);approval.verify(web);
        return new HandoverTemporaryAndroidBridge(web,approval,profile.attempt);
    }
    private void prepare(E2aCrossSourceProfile profile,HandoverTemporaryAndroidBridge bridge) throws Exception {
        TemporaryHandoverAndroidProcess.run(profile.attempt,"prepare");bridge.prepare();
        TemporaryHandoverAndroidProcess.run(profile.attempt,"stage");bridge.verify();
        TemporaryHandoverAndroidProcess.run(profile.attempt,"confirm");bridge.verifyConfirmation();
        bridge.activate();TemporaryHandoverAndroidProcess.run(profile.attempt,"activate");bridge.verifyActivation();
        TemporaryHandoverAndroidProcess.run(profile.attempt,"freeze");
    }
}
