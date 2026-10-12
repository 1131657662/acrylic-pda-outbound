package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.QuarantineHttpChecks;
import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Preserved P05 Android evidence is restored, never registered, signed or scanned again. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aQuarantineHistoryIT {
    @Test void originalAndroidQuarantineIsClearedThroughIndependentActualHttpReview() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=profile.connectQuarantineHistory();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        var originals=new CorrectionAccountingMigrationOriginals(jdbc,profile.attempt);
        var before=originals.snapshot("quarantine-original-schema");
        Flyway.configure().dataSource(jdbc.getDataSource()).target("87").load().migrate();
        var previous=originals.snapshot("quarantine-before-schema");
        for(var entry:before.entrySet()) {
            if(java.util.Set.of("`permission`","`role_permission`").contains(entry.getKey()))
                assertThat(previous.get(entry.getKey())).containsAll(entry.getValue());
            else assertThat(previous.get(entry.getKey())).as("original table %s",entry.getKey()).isEqualTo(entry.getValue());
        }
        originals=new CorrectionAccountingMigrationOriginals(jdbc,profile.attempt); var slice=originals.snapshot("quarantine-before");
        var migration=Flyway.configure().dataSource(jdbc.getDataSource()).load(); migration.migrate(); migration.validate();
        assertThat(originals.snapshot("quarantine-after-migration")).isEqualTo(slice);
        migration.migrate(); assertThat(originals.snapshot("quarantine-repeat-migration")).isEqualTo(slice);
        try(var context=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false",
            "--outbound.corrections.commands.enabled=true","--outbound.corrections.attachments.enabled=true")) {
            if("true".equals(System.getenv("QUARANTINE_WEB_PREPARE"))) QuarantineHttpChecks.prepareBrowser((WebApplicationContext)context,profile.attempt);
            else QuarantineHttpChecks.verify((WebApplicationContext)context,profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"true".equals(System.getenv("QUARANTINE_WEB_PREPARE"))
            ? "Original P05 nonempty migration/browser fixture only; clearance NOT tested"
            : "Original P05 Android/closed quarantine; nonempty migration and actual HTTP maker/checker clearance passed");
    }
}
