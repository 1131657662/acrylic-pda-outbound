package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.RecoveryPermissionDatabaseChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** New isolated original source, real FTWRL boundaries and ROW transactions; no business restoration. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aPermissionContinuityIT {
    @Test void verifiesLoggedPermissionBoundariesAndRejectsRevokeRestoreHistory() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker")); profile.migrate(jdbc, "34");
        var seed = E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage")); profile.migrate(jdbc);
        RecoveryPermissionDatabaseChecks.verify(jdbc, profile.attempt, seed);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Actual MySQL global read-lock permission boundaries, complete CRC32 binlog prefix and logged no-change interval; negative authority writes and released locks. No historical authority/actual business restoration.\n");
    }
}
