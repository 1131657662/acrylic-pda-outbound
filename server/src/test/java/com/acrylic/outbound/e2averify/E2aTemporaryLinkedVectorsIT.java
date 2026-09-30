package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryLinkedVectors;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** One fresh nonempty isolated database, using the actual HTTP command endpoints. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aTemporaryLinkedVectorsIT {
    @Test void exportsLinkedDecisionsForIndependentConsumption() throws Exception {
        var profile = new E2aCrossSourceProfile();
        var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34");
        E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission", Integer.class)).isPositive();
        profile.migrate(jdbc, "41");
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var context = E2aCrossSourceHttpIT.start(profile, "--outbound.temporary.commands.enabled=true")) {
            new TemporaryLinkedVectors((WebApplicationContext) context).write(profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Real HTTP LINK/linked NORMAL_CONFIRM/replay/original/detail/success; raw DB facts exported\n");
    }
}
