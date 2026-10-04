package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.HandoverApprovalWorkflowChecks;
import com.acrylic.outbound.inventory.HandoverPdaAssignmentChecks;
import com.acrylic.outbound.inventory.HandoverPdaManifestChecks;
import com.acrylic.outbound.inventory.HandoverPdaPackageChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aHandoverPdaManifestIT {
    @Test void namedPdaReadsVerifiedHandoverEvidenceAndOriginalPackage() throws Exception {
        var profile = new E2aCrossSourceProfile();
        var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34");
        E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "58");
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        String[] options = {"--outbound.handover.commands.enabled=true", "--server.servlet.session.cookie.secure=false"};
        try (var app = E2aCrossSourceHttpIT.start(profile, options)) {
            var workflow = new HandoverApprovalWorkflowChecks((WebApplicationContext) app, profile.attempt);
            workflow.verify();
            new HandoverPdaManifestChecks(workflow, profile.attempt).verify();
            new HandoverPdaPackageChecks(workflow, profile.attempt).verify();
            new HandoverPdaAssignmentChecks(workflow, profile.attempt).verify();
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Real HTTP PDA manifest, original package and target assignment after stop and approval; generated test identities only.");
    }
}
