package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Fresh actual source checkpoint and a distinct empty target; all originals retain their public pins. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aTemporaryEvidenceRestoreIT {
    @Test @EnabledIfEnvironmentVariable(named="E2A_TEMP_EVIDENCE_PHASE",matches="target")
    void consumesRestoredArchiveInSameClosedTransaction() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect(); Files.createFile(profile.attempt.resolve("test-started.marker"));
        try(var pool=new com.zaxxer.hikari.HikariDataSource()) {
            pool.setDataSource(jdbc.getDataSource()); pool.setMaximumPoolSize(3); pool.setMinimumIdle(1);
            RecoveryTemporaryEvidenceTargetChecks.consumeArchive(new org.springframework.jdbc.core.JdbcTemplate(pool),profile.attempt,
                Path.of(System.getenv("E2A_TEMP_EVIDENCE_SOURCE")));
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual dump import and closed archive consumer commit/rollback/refusal; no native verification, handover approval or normal admission\n");
    }

    @Test @EnabledIfEnvironmentVariable(named="E2A_TEMP_EVIDENCE_PHASE",matches="source")
    void capturesOriginalRowsAfterActualCheckpointAndControlledWindow() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect(); Files.createFile(profile.attempt.resolve("test-started.marker"));
        var migration=administrator(profile);
        profile.migrate(migration,"34"); var seed=E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage")); profile.migrate(migration);
        RecoveryTemporaryEvidenceSourceChecks.capture(jdbc,profile.attempt,seed);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual checkpoint and same-instance original native E2A/rows/registration/controlled window captured; synthetic development identity\n");
    }
    private static org.springframework.jdbc.core.JdbcTemplate administrator(E2aCrossSourceProfile profile) throws Exception {
        String password=Files.readAllLines(profile.attempt.resolve("private/root-client.cnf")).stream()
            .filter(line -> line.startsWith("password=")).findFirst().orElseThrow().substring(9);
        return new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(profile.url,"root",password));
    }
    @Test @EnabledIfEnvironmentVariable(named="E2A_TEMP_EVIDENCE_PHASE",matches="target")
    void restoresExactNativeEvidenceAndOwnersIntoNewEmptyTarget() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect(); Files.createFile(profile.attempt.resolve("test-started.marker"));
        try(var pool=new com.zaxxer.hikari.HikariDataSource()) {
            pool.setDataSource(jdbc.getDataSource()); pool.setMaximumPoolSize(3); pool.setMinimumIdle(1);
            RecoveryTemporaryEvidenceTargetChecks.verify(new org.springframework.jdbc.core.JdbcTemplate(pool),profile.attempt,
                Path.of(System.getenv("E2A_TEMP_EVIDENCE_SOURCE")));
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual dump import and closed original four-table restore/rollback/conflict/idempotence; normal admission remains closed\n");
    }

    @Test @EnabledIfEnvironmentVariable(named="E2A_TEMP_EVIDENCE_PHASE",matches="target")
    void readsRestoredArchiveWithoutInventingNativeServerVerification() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect(); Files.createFile(profile.attempt.resolve("test-started.marker"));
        try(var pool=new com.zaxxer.hikari.HikariDataSource()) {
            pool.setDataSource(jdbc.getDataSource()); pool.setMaximumPoolSize(3); pool.setMinimumIdle(1);
            RecoveryTemporaryEvidenceTargetChecks.verifyArchive(new org.springframework.jdbc.core.JdbcTemplate(pool),profile.attempt,
                Path.of(System.getenv("E2A_TEMP_EVIDENCE_SOURCE")));
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual dump import, closed four-table restore and complete recovered archive read/refusals; no native verification or ordinary admission invented\n");
    }
}
