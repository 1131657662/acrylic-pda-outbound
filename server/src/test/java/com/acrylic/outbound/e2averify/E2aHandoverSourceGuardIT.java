package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Real isolated MySQL transactions; no claim about authentic stop proofs or field devices. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverSourceGuardIT {
    @Test void sourceChangesPreventApprovalPreparationAndActivationWithoutEffects() throws Exception {
        var profile = new E2aCrossSourceProfile();
        var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34");
        E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "58");
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=true")) {
            var checks = new HandoverSourceChecks(app, profile.attempt);
            checks.request();
            checks.rejectsApprovalForNonterminalOriginal();
            checks.rejectsStaleSource("APPROVE");
            checks.approve();
            checks.prepare();
            checks.rejectsStaleSource("ACTIVATE");
            checks.status("SUCCEEDED");
            checks.rejected(checks.commands.command("ACTIVATE"), "HANDOVER_ORIGINAL_RESULT_OPEN");
            new HandoverSourceRace(checks, profile.attempt).successCommitsBeforeActivation();
            checks.activateAndReplay();
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Source refusals preserve sixteen tables; real submission lock race refuses; activation/original replay succeed\n");
    }
}
