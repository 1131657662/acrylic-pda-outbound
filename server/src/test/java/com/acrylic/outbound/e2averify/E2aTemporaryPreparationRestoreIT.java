package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Two distinct newly allocated datadirs: a native checkpoint producer and an empty restore target. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aTemporaryPreparationRestoreIT {
    @Test @EnabledIfEnvironmentVariable(named="E2A_TEMP_RESTORE_PHASE",matches="source")
    void dumpsOriginalAuthorizationBeforeNativePartsAndAck() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker")); profile.migrate(jdbc,"34");
        var seed = E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage")); profile.migrate(jdbc);
        RecoveryTemporaryRestoreSourceChecks.capture(jdbc,profile.attempt,seed);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Native synthetic original checkpoint dump and post-checkpoint original preparation/ACK captured; no restoration yet\n");
    }

    @Test @EnabledIfEnvironmentVariable(named="E2A_TEMP_RESTORE_PHASE",matches="target")
    void restoresRetainedPreparationIntoNewEmptyTargetWithoutRenewalOrNormalAdmission() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        var connection = new org.springframework.jdbc.datasource.SingleConnectionDataSource(jdbc.getDataSource().getConnection(),true);
        try { RecoveryTemporaryRestoreTargetChecks.verify(new org.springframework.jdbc.core.JdbcTemplate(connection),
            profile.attempt,Path.of(System.getenv("E2A_TEMP_RESTORE_SOURCE"))); }
        finally { connection.destroy(); }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual empty target import and native preparation/ACK closed restoration, rollback/refusal/idempotence; synthetic development identity/keys, ordinary admission closed\n");
    }
}
