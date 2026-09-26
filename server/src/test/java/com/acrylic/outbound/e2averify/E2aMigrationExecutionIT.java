package com.acrylic.outbound.e2averify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Opt-in target-34/35 execution; never runs in the default test task. */
@EnabledIfEnvironmentVariable(named = "E2A_MIGRATION_RUN", matches = "true")
class E2aMigrationExecutionIT {
    @Test
    void seedTarget34MigrateTarget35AndCapturePreservation() throws Exception {
        E2aMigrationRunner.main(new String[0]);
    }

}
