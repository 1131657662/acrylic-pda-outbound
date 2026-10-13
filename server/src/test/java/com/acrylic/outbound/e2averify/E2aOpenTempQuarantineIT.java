package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.OpenTempQuarantineSource;
import com.acrylic.outbound.inventory.QuarantineHttpChecks;
import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aOpenTempQuarantineIT {
    @Test void preservedRejectedAndroidSourceAllowsPerBaleClearanceWithoutClosingScope() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=connect(profile);
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        var originals=new CorrectionAccountingMigrationOriginals(jdbc,profile.attempt);
        var before=originals.snapshot("open-temp-before");
        var migration=Flyway.configure().dataSource(jdbc.getDataSource()).load(); migration.migrate(); migration.validate();
        var after=originals.snapshot("open-temp-after-migration");
        for(var entry:before.entrySet()) {
            if(java.util.Set.of("`permission`","`role_permission`").contains(entry.getKey())) assertThat(after.get(entry.getKey())).containsAll(entry.getValue());
            else assertThat(after.get(entry.getKey())).as("original %s",entry.getKey()).isEqualTo(entry.getValue());
        }
        migration.migrate(); assertThat(originals.snapshot("open-temp-repeat-migration")).isEqualTo(after);
        try(var app=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false",
            "--outbound.temporary.commands.enabled=true","--outbound.corrections.commands.enabled=true","--outbound.corrections.attachments.enabled=true")) {
            OpenTempQuarantineSource.prepare((WebApplicationContext)app,profile.attempt);
            if("true".equals(System.getenv("QUARANTINE_WEB_PREPARE"))) QuarantineHttpChecks.prepareBrowser((WebApplicationContext)app,profile.attempt);
            else QuarantineHttpChecks.verify((WebApplicationContext)app,profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Original Android REJECTED source; migration and original per-bale disposition; "
            +("true".equals(System.getenv("QUARANTINE_WEB_PREPARE"))?"browser fixture only; clearance untested":"independent HTTP clearance without source closure"));
    }
    private JdbcTemplate connect(E2aCrossSourceProfile profile) {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(profile.url,profile.user,System.getenv("E2A_CROSS_PASSWORD")));
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo(profile.database);
        assertThat(jdbc.queryForObject("SELECT CURRENT_USER()",String.class)).isEqualTo(profile.user+"@127.0.0.1");
        assertThat(jdbc.queryForObject("SELECT @@port",Integer.class)).isEqualTo(19330);
        assertThat(jdbc.queryForObject("SELECT @@datadir",String.class).replace('\\','/'))
            .isEqualToIgnoringCase(profile.attempt.resolve("data").toString().replace('\\','/')+"/");
        assertThat(Files.isRegularFile(profile.attempt.resolve("open-temp-source-sha256.txt"))).isTrue(); return jdbc;
    }
}
