package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.RecoveryControlledPermissionDatabaseChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Original source authority profile, not current permissions in a restored database. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aControlledPermissionIT {
    @Test void constrainsOriginalSourceBeforeCapturingHistoricalPermissionWindow() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        var migration = administrator(profile);
        Files.createFile(profile.attempt.resolve("test-started.marker")); profile.migrate(migration, "34");
        var seed = E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage")); profile.migrate(migration);
        RecoveryControlledPermissionDatabaseChecks.verify(jdbc, profile.attempt, seed);
        Files.writeString(profile.attempt.resolve("test-completed.marker"), "Controlled original DML writer/root locked, real historical permission boundaries and raw log pins; no ordinary admission or actual business restoration.\n");
    }

    private static org.springframework.jdbc.core.JdbcTemplate administrator(E2aCrossSourceProfile profile) throws Exception {
        String password = Files.readAllLines(profile.attempt.resolve("private/root-client.cnf")).stream()
            .filter(line -> line.startsWith("password=")).findFirst().orElseThrow().substring(9);
        return new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
            profile.url, "root", password));
    }
}
