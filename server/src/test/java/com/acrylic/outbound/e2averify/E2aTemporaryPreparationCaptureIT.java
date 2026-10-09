package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.RecoveryTemporaryPreparationDatabaseChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Fresh native preparation rows and nonempty files; no HTTP, current permission or actual restoration claim. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aTemporaryPreparationCaptureIT {
    @Test void capturesOriginalTemporaryPreparationAndAckWithoutChangingFacts() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker")); profile.migrate(jdbc, "34");
        var seed = E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage")); profile.migrate(jdbc);
        RecoveryTemporaryPreparationDatabaseChecks.verify(jdbc, profile.attempt, seed);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Actual MySQL original TEMP preparation+ACK capture/offline pin/native compressed originals preserved; synthetic source/development signatures, no business restoration\n");
    }
}
