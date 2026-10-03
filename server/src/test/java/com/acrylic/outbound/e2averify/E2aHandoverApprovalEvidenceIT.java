package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.HandoverApprovalEvidenceChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aHandoverApprovalEvidenceIT {
    @Test void attestedDurableRecoveryIsConsumedWithoutRepeatingStopEffects() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "58"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        String[] options = {"--outbound.handover.commands.enabled=true", "--server.servlet.session.cookie.secure=false"};
        HandoverApprovalEvidenceChecks checks;
        try (var app = E2aCrossSourceHttpIT.start(profile, options)) {
            checks = new HandoverApprovalEvidenceChecks((WebApplicationContext) app, profile.attempt);
            checks.verify((WebApplicationContext) app);
        }
        try (var app = E2aCrossSourceHttpIT.start(profile, options)) { checks.restart((WebApplicationContext) app); }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Real stop and original-device abort HTTP, service-level evidence consumption, missing pair/highwater/file/digest refusals, full-table no-effect checks, restart consumption and original stop replay. Generated EC identities. Approval entry and physical checklist are not exercised.");
    }
}
