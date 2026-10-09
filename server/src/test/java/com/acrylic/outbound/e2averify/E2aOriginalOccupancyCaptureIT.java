package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.RecoveryOriginalOccupancyDatabaseChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Fresh actual MySQL; synthetic first-round source and development receipts, no business restoration. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aOriginalOccupancyCaptureIT {
    @Test void capturesActualOccupancyWithoutChangingCommittedTablesOrFiles() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker")); profile.migrate(jdbc, "34");
        var seed = E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage")); profile.migrate(jdbc);
        RecoveryOriginalOccupancyDatabaseChecks.verify(jdbc, profile.attempt, seed);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Actual MySQL original submitted ABORTED occupancy observation + offline pin; all facts preserved; synthetic source/development receipts; no business restoration\n");
    }
}
