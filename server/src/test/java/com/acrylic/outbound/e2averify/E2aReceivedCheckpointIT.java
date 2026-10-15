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
    void verifiesOperationsApprovalAcrossOriginalCheckpoint() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect(); Files.createFile(profile.attempt.resolve("test-started.marker"));
        Path original=Path.of(System.getenv("E2A_RECEIVED_SOURCE"));
        RecoveryReceivedCheckpointTargetChecks.verify(jdbc,profile.attempt,original);
        E2aReceivedCheckpointUpgrade.verify(profile,jdbc);
        for(String file:new String[]{"server-private.der","server-public.der"})
            Files.copy(original.resolve("private").resolve(file),profile.attempt.resolve("private").resolve(file));
        try(var app=E2aCrossSourceHttpIT.start(profile,"--outbound.handover.commands.enabled=true",
                "--outbound.handover.fault.enabled=true","--server.servlet.session.cookie.secure=false")) {
            RecoveryReceivedOperationsChecks.verify((WebApplicationContext)app,profile.attempt,original);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Synthetic independent operations signature bound to actual recovered physical proof; no release or qualification");
    }
    @Test @EnabledIfEnvironmentVariable(named="E2A_RECEIVED_PHASE",matches="target")
    void replaysPhysicalApprovalInNewJvm() throws Exception {
        var profile=E2aCrossSourceProfile.completedPhysicalReview();
        try(var app=E2aCrossSourceHttpIT.start(profile,"--outbound.handover.commands.enabled=true",
                "--outbound.handover.fault.enabled=true","--server.servlet.session.cookie.secure=false")) {
            RecoveryReceivedPhysicalRestart.verify((WebApplicationContext)app,profile.attempt,Path.of(System.getenv("E2A_RECEIVED_SOURCE")));
        }
    }
    @Test @EnabledIfEnvironmentVariable(named="E2A_RECEIVED_PHASE",matches="target")
    void approvesPhysicalScopeAcrossOriginalCheckpoint() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect(); Files.createFile(profile.attempt.resolve("test-started.marker"));
        Path original=Path.of(System.getenv("E2A_RECEIVED_SOURCE"));
        RecoveryReceivedCheckpointTargetChecks.verify(jdbc,profile.attempt,original);
        E2aReceivedCheckpointUpgrade.verify(profile,jdbc);
        for(String file:new String[]{"server-private.der","server-public.der"})
            Files.copy(original.resolve("private").resolve(file),profile.attempt.resolve("private").resolve(file));
        try(var app=E2aCrossSourceHttpIT.start(profile,"--outbound.handover.commands.enabled=true",
                "--outbound.handover.fault.enabled=true","--server.servlet.session.cookie.secure=false")) {
            RecoveryReceivedPhysicalReviewChecks.verify((WebApplicationContext)app,profile.attempt,original);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Synthetic supervisor physical approval reconciled to original source; no release or new qualification");
    }
    @Test @EnabledIfEnvironmentVariable(named="E2A_RECEIVED_PHASE",matches="target")
    void reconcilesGlobalNumberHistoryAcrossOriginalCheckpoint() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect(); Files.createFile(profile.attempt.resolve("test-started.marker"));
        Path original=Path.of(System.getenv("E2A_RECEIVED_SOURCE"));
        RecoveryReceivedCheckpointTargetChecks.verify(jdbc,profile.attempt,original);
        E2aReceivedCheckpointUpgrade.verify(profile,jdbc);
        for(String file:new String[]{"server-private.der","server-public.der"})
            Files.copy(original.resolve("private").resolve(file),profile.attempt.resolve("private").resolve(file));
        try(var app=E2aCrossSourceHttpIT.start(profile,"--outbound.handover.commands.enabled=true",
                "--outbound.handover.fault.enabled=true","--server.servlet.session.cookie.secure=false")) {
            RecoveryReceivedNumberHistoryChecks.verify((WebApplicationContext)app,profile.attempt,original);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Original global number row history reconciled against independent checkpoint pins and closed target; no release\n");
    }
    @Test @EnabledIfEnvironmentVariable(named="E2A_RECEIVED_PHASE",matches="target")
    void verifiesOriginalHistoryCoverageForActualRecoveryLossWindow() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect(); Files.createFile(profile.attempt.resolve("test-started.marker"));
        Path original=Path.of(System.getenv("E2A_RECEIVED_SOURCE"));
        RecoveryReceivedCheckpointTargetChecks.verify(jdbc,profile.attempt,original);
        E2aReceivedCheckpointUpgrade.verify(profile,jdbc);
        for(String file:new String[]{"server-private.der","server-public.der"})
            Files.copy(original.resolve("private").resolve(file),profile.attempt.resolve("private").resolve(file));
        try(var app=E2aCrossSourceHttpIT.start(profile,"--outbound.handover.commands.enabled=true",
                "--outbound.handover.fault.enabled=true","--server.servlet.session.cookie.secure=false")) {
            RecoveryReceivedHistoryCoverageChecks.verify((WebApplicationContext)app,profile.attempt,original);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual original binlog coverage bound to synthetic target loss window; no release\n");
    }
    @Test @EnabledIfEnvironmentVariable(named="E2A_RECEIVED_PHASE",matches="target")
    void inventoriesRecoveredScopeAfterSupervisorFenceWithoutReleasing() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect(); Files.createFile(profile.attempt.resolve("test-started.marker"));
        Path original=Path.of(System.getenv("E2A_RECEIVED_SOURCE"));
        RecoveryReceivedCheckpointTargetChecks.verify(jdbc,profile.attempt,original);
        for(String file:new String[]{"server-private.der","server-public.der"})
            Files.copy(original.resolve("private").resolve(file),profile.attempt.resolve("private").resolve(file));
        try(var app=E2aCrossSourceHttpIT.start(profile,"--outbound.handover.commands.enabled=true",
                "--outbound.handover.fault.enabled=true","--server.servlet.session.cookie.secure=false")) {
            RecoveryReceivedReleaseChecks.verify((WebApplicationContext)app,profile.attempt,original);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual closed supervisor-fenced range inventory; missing closure and release remain explicit\n");
    }
    @Test @EnabledIfEnvironmentVariable(named="E2A_RECEIVED_PHASE",matches="target")
    void fencesRecoveredActualTemporarySourceWithIndependentSupervisor() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect(); Files.createFile(profile.attempt.resolve("test-started.marker"));
        Path original=Path.of(System.getenv("E2A_RECEIVED_SOURCE"));
        RecoveryReceivedCheckpointTargetChecks.verify(jdbc,profile.attempt,original);
        for(String file:new String[]{"server-private.der","server-public.der"})
            Files.copy(original.resolve("private").resolve(file),profile.attempt.resolve("private").resolve(file));
        try(var app=E2aCrossSourceHttpIT.start(profile,"--outbound.handover.commands.enabled=true",
                "--outbound.handover.fault.enabled=true","--server.servlet.session.cookie.secure=false")) {
            RecoveryReceivedFaultChecks.verify((WebApplicationContext)app,profile.attempt,original);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual recovered originals -> existing REQUEST/independent supervisor FAULT_FENCE in closed transaction; no ordinary admission or new qualification\n");
    }
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
