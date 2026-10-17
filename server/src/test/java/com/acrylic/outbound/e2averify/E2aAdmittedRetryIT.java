package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.RecoveryExportInput;
import com.acrylic.outbound.recovery.RecoveryRetirementApplicationFixture;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Actual HTTP retries with default-off and P5 admission; every identity is synthetic. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aAdmittedRetryIT {
    @Test void retryKeepsFrozenFactsAndAuthorityAcrossTransactions() throws Exception {
        var profile = new E2aCrossSourceProfile(); var raw = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(raw, "34"); E2aV34Seed.seed(raw, profile.attempt.resolve("storage"));
        profile.migrate(raw); E2aCrossSourceHttpSetup.signing(profile.attempt);
        RecoveryExportInput input;
        try (var web = start(profile, false, null)) {
            input = RecoveryExportInput.seed((WebApplicationContext) web);
            try (var worker = start(profile, true, null)) {
                new RecoveryRetryScenario(new RecoveryRetryScenario.Supply(profile, raw, input), "off").positive();
            }
        }
        var p5 = new RecoveryRetirementApplicationFixture(profile.attempt);
        try (var web = start(profile, false, p5.options()); var worker = start(profile, true, p5.options())) {
            var scenario = new RecoveryRetryScenario(new RecoveryRetryScenario.Supply(profile, raw, input), "p5");
            scenario.positive(); scenario.failures();
            p5.drain(web, worker);
        }
        p5.nextProcessStarts();
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "default-off/P5 actual retry; frozen XLSX/PDF; concurrent key; conflict; tail rollback; lock-wait revocation; drained successor");
    }

    private static ConfigurableApplicationContext start(E2aCrossSourceProfile profile, boolean worker, String[] admission) {
        var options = new ArrayList<String>(List.of("--server.servlet.session.cookie.secure=false",
            "--outbound.exports.sample-data=true", "--outbound.exports.renderer-home="
                + E2aWorktree.ROOT.resolve("report-rendering/build/install/acrylic-report-rendering"),
            "--outbound.exports.browsers=" + E2aWorktree.ROOT.resolve("report-rendering/build/browsers")));
        if (admission != null) options.addAll(List.of(admission));
        if (worker) options.addAll(List.of("--spring.main.web-application-type=none", "--spring.profiles.active=worker",
            "--outbound.worker.enabled=true", "--outbound.worker.poll-ms=250", "--outbound.worker.reminder-poll-ms=3600000"));
        return E2aCrossSourceHttpIT.start(profile, options.toArray(String[]::new));
    }
}
