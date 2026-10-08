package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.*;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aHandoverTemporaryMixedContinuationAndroidIT {
    @Test void receivingDeviceContinuesItsCumulativeHandoverRoundThroughR06ToSuccess() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));profile.migrate(jdbc,"72");
        E2aCrossSourceHttpSetup.signing(profile.attempt);HandoverTemporaryAndroidBridge bridge;
        try(var app=E2aCrossSourceHttpIT.start(profile,options(false))) {
            var web=(WebApplicationContext)app;bridge=source(profile,web);bridge.enableShortWork();
            prepare(profile,bridge);bridge.intakeDisabled();
        }
        try(var app=E2aCrossSourceHttpIT.start(profile,options(true))) {
            var web=(WebApplicationContext)app;bridge.verifyIntake(web);TemporaryHandoverAndroidProcess.run(profile.attempt,"upload");
            TemporaryHandoverAndroidProcess.run(profile.attempt,"evidence");bridge.stop(web,"before");
            TemporaryHandoverAndroidProcess.run(profile.attempt,"stop");bridge.stop(web,"after");
        }
        HandoverEvidenceMigrationChecks.verify(profile,jdbc);
        try(var app=E2aCrossSourceHttpIT.start(profile,options(true))) {
            var web=(WebApplicationContext)app;
            bridge.continuation(web,"activate");TemporaryHandoverAndroidProcess.run(profile.attempt,"continuation");
            bridge.continuation(web,"complete");
        }
        try(var app=E2aCrossSourceHttpIT.start(profile,options(true))) {
            TemporaryHandoverAndroidProcess.run(profile.attempt,"continuationSuccess");
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual Android A -> TEMP handover B -> R06 continuation B -> independently confirmed success. Immutable mixed prefixes, retained owners, local counter overlap, SQL/Room tail rollback, lost responses and restart; synthetic identities/development keys.\n");
    }
    private String[] options(boolean intake) {
        var options=new ArrayList<>(Arrays.asList(E2aHandoverTemporarySubmissionAndroidIT.options(intake)));
        options.add("--outbound.temporary.continuation-intake.enabled="+intake);return options.toArray(String[]::new);
    }
    private HandoverTemporaryAndroidBridge source(E2aCrossSourceProfile profile,WebApplicationContext web) throws Exception {
        E2aAndroidPreparation.write(web,profile.attempt.resolve("private"),true);
        var input=profile.attempt.resolve("private/android-http-input.json");
        var contract=(com.fasterxml.jackson.databind.node.ObjectNode)com.acrylic.outbound.submissions.SubmissionJson.parse(Files.readAllBytes(input));
        contract.put("otherSourceBeforeFreeze",true).put("plannedCount","2");
        Files.writeString(input,com.acrylic.outbound.submissions.SubmissionJson.canonical(contract));E2aAndroidProcess.run(profile.attempt);
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
