package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aHandoverTemporarySubmissionAndroidIT {
    @Test void actualRecipientWorksUploadsAndConsumesIndependentHandoverSuccess() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));profile.migrate(jdbc);
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        HandoverTemporaryAndroidBridge bridge;
        try(var app=E2aCrossSourceHttpIT.start(profile,options(false))) {
            var web=(WebApplicationContext)app;bridge=source(profile,web);bridge.enableWork();
            TemporaryHandoverAndroidProcess.run(profile.attempt,"prepare");bridge.prepare();
            TemporaryHandoverAndroidProcess.run(profile.attempt,"stage");bridge.verify();
            TemporaryHandoverAndroidProcess.run(profile.attempt,"confirm");bridge.verifyConfirmation();
            bridge.activate();TemporaryHandoverAndroidProcess.run(profile.attempt,"activate");bridge.verifyActivation();
            TemporaryHandoverAndroidProcess.run(profile.attempt,"freeze");bridge.intakeDisabled();
        }
        try(var app=E2aCrossSourceHttpIT.start(profile,options(true))) {
            var web=(WebApplicationContext)app;bridge.verifyIntake(web);
            TemporaryHandoverAndroidProcess.run(profile.attempt,"upload");bridge.completeWork(web);
        }
        try(var restarted=E2aCrossSourceHttpIT.start(profile,options(true))) {
            TemporaryHandoverAndroidProcess.run(profile.attempt,"success");
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Actual source/recipient Android and isolated HTTP/MySQL: independent handover, own ACK, new scan, cumulative freeze, upload, final SQL rollback, accounting and success after restart. Synthetic identity/keys.\n");
    }
    static String[] options(boolean intake) {
        return new String[]{"--outbound.handover.commands.enabled=true","--outbound.temporary.handover-intake.enabled="+intake,
            "--outbound.temporary.commands.enabled=true","--server.servlet.session.cookie.secure=false"};
    }
    static HandoverTemporaryAndroidBridge source(E2aCrossSourceProfile profile,WebApplicationContext web) throws Exception {
        E2aAndroidPreparation.write(web,profile.attempt.resolve("private"));
        var input=profile.attempt.resolve("private/android-http-input.json");
        var contract=(com.fasterxml.jackson.databind.node.ObjectNode)SubmissionJson.parse(Files.readString(input));
        contract.put("otherSourceBeforeFreeze",true);Files.writeString(input,SubmissionJson.canonical(contract));
        E2aAndroidProcess.run(profile.attempt);
        var approval=new HandoverTemporaryApprovalChecks(web,profile.attempt);approval.verify(web);
        return new HandoverTemporaryAndroidBridge(web,approval,profile.attempt);
    }
}
