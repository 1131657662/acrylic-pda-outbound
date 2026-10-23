package com.acrylic.outbound.e2averify;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Full restored database registration plus live P5 in the same two product JVMs. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aRestoredClosedP5IT {
    @Test void registeredRestoreServesExportsWithExplicitSyntheticP5Supply() throws Exception {
        var profile = new E2aCrossSourceProfile(); profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        try (var pool = E2aPointInTimeCheckpoint.pool(profile, profile.url)) {
            var source = new JdbcTemplate(pool); profile.migrate(source, "34");
            var seed = E2aV34Seed.seed(source, profile.attempt.resolve("storage"));
            profile.migrate(source); E2aCrossSourceHttpSetup.signing(profile.attempt);
            var input = E2aRestoredClosedAdmissionIT.prepare(profile, source);
            var restored = E2aRestoredClosedAdmissionIT.restore(profile, source, seed);
            var admitted = restored.copier().snapshot(restored.target(), restored.copier().restored.resolve("storage"));
            var p5 = restored.supply().enableP5(profile.attempt);
            assertThat(restored.copier().snapshot(restored.target(), restored.copier().restored.resolve("storage")))
                .isEqualTo(admitted);
            var authority = restored.supply().originals();
            E2aRestoredClosedAdmissionIT.wrongSchema(profile, source, restored);
            E2aRestoredClosedAdmissionIT.exercise(profile, input, restored, "restored-closed-p5");
            p5.finish();
            assertThat(restored.supply().originals()).isEqualTo(authority);
            E2aRestoredClosedAdmissionIT.save(profile, "authority-originals.json", authority);
            var finalSource = restored.copier().snapshot(source, profile.attempt.resolve("storage"));
            assertThat(finalSource).isEqualTo(restored.before());
            E2aRestoredClosedAdmissionIT.save(profile, "source-final.json", finalSource);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Full unbound restored registration/API/worker/files with P5; explicit test-only synthetic identity promotion",
            StandardOpenOption.CREATE_NEW);
    }
}
