package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.E2aAndroidPreparation;
import com.acrylic.outbound.recovery.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aReceivedCheckpointIT {
    @Test @EnabledIfEnvironmentVariable(named="E2A_RECEIVED_PHASE",matches="target")
    void consumesActualReceivedArchiveWithinClosedTransaction() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect(); Files.createFile(profile.attempt.resolve("test-started.marker"));
        RecoveryReceivedArchiveChecks.verify(jdbc,profile.attempt,Path.of(System.getenv("E2A_RECEIVED_SOURCE")));
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual received archive consumed with native original in closed transaction; no admission\n");
    }
    @Test @EnabledIfEnvironmentVariable(named="E2A_RECEIVED_PHASE",matches="source")
    void retainsActualPermissionHistoryAcrossAndroidCheckpoint() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect(); Files.createFile(profile.attempt.resolve("test-started.marker"));
        String secret=Files.readAllLines(profile.attempt.resolve("private/root-client.cnf")).stream()
            .filter(line -> line.startsWith("password=")).findFirst().orElseThrow().substring(9);
        var admin=new org.springframework.jdbc.datasource.DriverManagerDataSource(profile.url,"root",secret);
        org.flywaydb.core.Flyway.configure().dataSource(admin).target("34").load().migrate();
        E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        org.flywaydb.core.Flyway.configure().dataSource(admin).load().migrate();
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try(var app=E2aCrossSourceHttpIT.start(profile)) {
            E2aAndroidPreparation.write((WebApplicationContext)app,profile.attempt.resolve("private"));
        }
        var begin=RecoveryReceivedCheckpointEnrollment.prepare(jdbc,profile.attempt);
        try(var app=E2aCrossSourceHttpIT.start(profile)) {
            E2aAndroidProcess.run(profile.attempt);
            RecoveryReceivedTemporarySourceChecks.capture((WebApplicationContext)app,profile.attempt);
        }
        RecoveryReceivedCheckpointSourceChecks.verify(jdbc,profile.attempt,begin);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual checkpoint before Android scan, controlled history and strict initial source verified; development identity only\n");
    }

    @Test @EnabledIfEnvironmentVariable(named="E2A_RECEIVED_PHASE",matches="target")
    void rebuildsActualCheckpointWithOriginalBinlogAndConservesAllFacts() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect(); Files.createFile(profile.attempt.resolve("test-started.marker"));
        Path original=Path.of(System.getenv("E2A_RECEIVED_SOURCE"));
        RecoveryReceivedCheckpointRefusalChecks.verify(jdbc,profile.attempt,original);
        RecoveryReceivedCheckpointTargetChecks.verify(jdbc,profile.attempt,original);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual temporal checkpoint imported and original binlog applied to new target; all tables and files conserved, no admission\n");
    }
}
