package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.RecoveryUnsubmittedOccupancyDatabaseChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Fresh real MySQL for original unsubmitted TEMP observations; no HTTP or actual restoration. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aUnsubmittedOccupancyCaptureIT {
    @Test void observesOriginalEvidenceReservationsWithoutChangingCommittedTablesOrFiles() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker")); profile.migrate(jdbc, "34");
        var seed = E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage")); profile.migrate(jdbc);
        RecoveryUnsubmittedOccupancyDatabaseChecks.verify(jdbc, profile.attempt, seed);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Actual MySQL unsubmitted ABORTED/SOURCE_RESERVED/E2A original references + offline independent pin; all facts preserved; synthetic source/development keys; no business restoration\n");
    }
}
