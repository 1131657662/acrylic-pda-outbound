package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

/** Original completed TEMP quarantine rows, references and trigger evidence survive the PC schema extension. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aPcQuarantineHistoryIT {
    @Test void originalCompletedTempReferencesSurvivePcExtension() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=connect(profile);
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        var originals=new CorrectionAccountingMigrationOriginals(jdbc,profile.attempt);
        var before=originals.snapshot("pc-extension-history-before");
        var migration=Flyway.configure().dataSource(jdbc.getDataSource()).load();
        migration.migrate(); migration.validate();
        assertThat(originals.snapshot("pc-extension-history-after")).isEqualTo(before);
        migration.migrate(); assertThat(originals.snapshot("pc-extension-history-repeat")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_origin WHERE disposition_id IS NOT NULL AND pc_disposition_id IS NULL",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_origin WHERE pc_disposition_id IS NOT NULL",Integer.class)).isZero();
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Preserved completed TEMP originals; nonempty PC extension and repeat migration conservation");
    }
    private JdbcTemplate connect(E2aCrossSourceProfile profile) {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(profile.url,profile.user,System.getenv("E2A_CROSS_PASSWORD")));
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo(profile.database);
        assertThat(jdbc.queryForObject("SELECT @@port",Integer.class)).isEqualTo(19330);
        assertThat(jdbc.queryForObject("SELECT @@datadir",String.class).replace('\\','/'))
            .isEqualToIgnoringCase(profile.attempt.resolve("data").toString().replace('\\','/')+"/");
        assertThat(Files.isRegularFile(profile.attempt.resolve("pc-extension-source-sha256.txt"))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_origin",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_release",Integer.class)).isEqualTo(1);
        return jdbc;
    }
}
