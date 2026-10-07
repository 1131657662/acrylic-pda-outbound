package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.HandoverApprovalWorkflowChecks;
import com.acrylic.outbound.inventory.HandoverRemovedChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aHandoverApprovalWorkflowIT {
    @Test void authenticStopAndFullChecklistGuardApprovalPreparationAndActivation()throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        profile.migrate(jdbc);E2aCrossSourceHttpSetup.signing(profile.attempt);
        String[] options={"--outbound.handover.commands.enabled=true","--server.servlet.session.cookie.secure=false"};
        HandoverApprovalWorkflowChecks checks;
        try(var app=E2aCrossSourceHttpIT.start(profile,options)) {
            checks=new HandoverApprovalWorkflowChecks((WebApplicationContext)app,profile.attempt);checks.verify();
            new HandoverRemovedChecks((WebApplicationContext)app,profile.attempt).verify();
        }
        try(var app=E2aCrossSourceHttpIT.start(profile,options)){checks.restart((WebApplicationContext)app);}
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual stop/abort and supervisor HTTP approval/prepare/activate; full checklist, signature/file and stock drift refusals; approved physical removal bindings and retained count; rollback and restart replay. Generated identities and physical descriptions, no field acceptance.");
    }
}
