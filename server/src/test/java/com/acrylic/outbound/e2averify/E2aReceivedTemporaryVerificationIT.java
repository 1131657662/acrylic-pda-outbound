package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.E2aAndroidPreparation;
import com.acrylic.outbound.recovery.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aReceivedTemporaryVerificationIT {
    @Test @EnabledIfEnvironmentVariable(named="E2A_RECEIVED_PHASE",matches="source")
    void capturesActualReceivedNativeSourceWithoutChangingOriginalRows() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect(); Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34"); E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage")); profile.migrate(jdbc);
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try(var app=E2aCrossSourceHttpIT.start(profile)) {
            E2aAndroidPreparation.write((WebApplicationContext)app,profile.attempt.resolve("private"));
            E2aAndroidProcess.run(profile.attempt);
            RecoveryReceivedTemporarySourceChecks.capture((WebApplicationContext)app,profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual development Android/HTTP received native source captured read-only; explicit missing-verification dump, not temporal cutoff\n");
    }

    @Test @EnabledIfEnvironmentVariable(named="E2A_RECEIVED_PHASE",matches="target")
    void restoresOriginalVerificationWithAllReceivedPrerequisitesConserved() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connect(); Files.createFile(profile.attempt.resolve("test-started.marker"));
        try(var pool=new com.zaxxer.hikari.HikariDataSource()) {
            pool.setDataSource(jdbc.getDataSource()); pool.setMaximumPoolSize(3); pool.setMinimumIdle(1);
            pool.setConnectionInitSql("SET SESSION innodb_lock_wait_timeout=1");
            RecoveryReceivedTemporaryTargetChecks.verify(new org.springframework.jdbc.core.JdbcTemplate(pool),profile.attempt,
                Path.of(System.getenv("E2A_RECEIVED_SOURCE")));
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual new target missing-verification import and exact original restoration; no handover admission or temporal-cutoff claim\n");
    }
}
