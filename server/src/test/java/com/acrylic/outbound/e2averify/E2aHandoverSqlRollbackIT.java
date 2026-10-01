package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;

/** Real isolated MySQL service transactions; synthetic identity/stop proof, no HTTP or device claim. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverSqlRollbackIT {
    @Test void auditInsertFailureRollsBackActivationBeforeOriginalCommandRecovery() throws Exception {
        var profile = new E2aCrossSourceProfile();
        var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34");
        E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "51");
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        HandoverSqlRollbackChecks checks;
        try (var context = start(profile)) {
            checks = new HandoverSqlRollbackChecks(context, profile.attempt);
            checks.failureRollsBack();
            checks.originalKeyRecovers();
        }
        try (var restarted = start(profile)) { checks.replayAfterRestart(restarted); }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "SQL after-audit rollback; sixteen typed snapshots equal; same-key recovery/restart; nonempty K retained\n");
    }

    private ConfigurableApplicationContext start(E2aCrossSourceProfile profile) {
        return E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=true");
    }
}
