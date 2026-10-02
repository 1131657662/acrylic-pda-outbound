package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.handover.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real isolated MySQL service transactions; no HTTP/PDA/worker or power-loss claim. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverRollbackIT {
    @Test void failureAfterOutboxReceiptAndAuditRollsBackBeforeOriginalKeyRecovery() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "50"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        HandoverRollbackChecks checks;
        try (var context = start(profile)) {
            checks = new HandoverRollbackChecks(context, profile.attempt);
            checks.failureRollsBack();
            checks.originalKeyRecovers();
        }
        try (var restarted = start(profile)) { checks.replayAfterRestart(restarted); }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "after-audit injected rollback; 16 table snapshots equal; same-key commit/replay/context restart; nonempty K retained\n");
    }

    private ConfigurableApplicationContext start(E2aCrossSourceProfile profile) {
        return E2aCrossSourceHttpIT.start(profile, new Class<?>[]{HandoverAuditFaultConfiguration.class},
            "--outbound.handover.commands.enabled=true");
    }
}
