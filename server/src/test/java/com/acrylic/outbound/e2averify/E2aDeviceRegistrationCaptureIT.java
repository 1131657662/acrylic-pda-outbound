package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.RecoveryDeviceRegistrationDatabaseChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** One fresh isolated schema; no HTTP, private key, native business restoration or production registration. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aDeviceRegistrationCaptureIT {
    @Test void capturesActualRegistrationWithoutChangingNonemptyFacts() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker")); profile.migrate(jdbc, "34");
        E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage")); profile.migrate(jdbc);
        RecoveryDeviceRegistrationDatabaseChecks.verify(jdbc, profile.attempt);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Real MySQL pre-failure device registration read seam; development P256/synthetic identity/control; all input facts preserved; no business recovery\n");
    }
}
