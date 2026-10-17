package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.RecoveryImportInput;
import com.acrylic.outbound.recovery.RecoveryRetirementApplicationFixture;
import com.acrylic.outbound.recovery.RecoveryOperationRegistry;
import java.nio.file.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Actual multipart/file/transaction path on a fresh synthetic nonempty candidate. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aAdmittedImportIT {
    @Test void admittedMultipartKeepsFileAndTransactionWithinWriterRoot() throws Exception {
        var profile = new E2aCrossSourceProfile(); var raw = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(raw, "34"); E2aV34Seed.seed(raw, profile.attempt.resolve("storage"));
        profile.migrate(raw); E2aCrossSourceHttpSetup.signing(profile.attempt);
        RecoveryImportInput input;
        try (var web = start(profile, null)) {
            input = RecoveryImportInput.seed(web);
            new RecoveryImportScenario(profile, raw, input, web, "off").positive();
        }
        var p5 = new RecoveryRetirementApplicationFixture(profile.attempt);
        try (var web = start(profile, p5.options())) {
            var scenario = new RecoveryImportScenario(profile, raw, input, web, "p5");
            scenario.positive(); new RecoveryImportFaults(scenario).verify();
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(web.getBean(RecoveryOperationRegistry.class).liveRoots()).isZero());
            scenario.probe("FREE", "after-drain");
            Files.copy(profile.attempt.resolve("private/p5-retirement/p5/leases/index.json"),
                profile.attempt.resolve("private/p5-retirement/drained-index.json"));
        }
        p5.nextProcessStarts();
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "actual off/P5 multipart; immutable files; replay/conflict/permissions/concurrent; held file root; tail rollback; lock-wait revoke; successor");
    }

    private static ConfigurableApplicationContext start(E2aCrossSourceProfile profile, String[] admission) {
        var options = new java.util.ArrayList<String>(java.util.List.of("--server.servlet.session.cookie.secure=false"));
        if (admission != null) options.addAll(java.util.List.of(admission));
        return E2aCrossSourceHttpIT.start(profile, options.toArray(String[]::new));
    }
}
