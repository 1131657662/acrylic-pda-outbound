package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

/** Preserved closed TEMP data remains compatible with per-bale TEMP clearance. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aOpenTempQuarantineHistoryIT {
    @Test void completedClosedTempOriginalColumnsSurvivePerBaleExtension() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=connect(profile);
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        var originals=new CorrectionAccountingMigrationOriginals(jdbc,profile.attempt);
        var before=originals.snapshot("open-temp-closed-history-before");
        var migration=Flyway.configure().dataSource(jdbc.getDataSource()).load();
        migration.migrate(); migration.validate();
        assertThat(originals.snapshot("open-temp-closed-history-after")).isEqualTo(before);
        migration.migrate(); assertThat(originals.snapshot("open-temp-closed-history-repeat")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_origin WHERE disposition_id IS NOT NULL",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_origin WHERE formal_disposition_id IS NOT NULL OR pc_disposition_id IS NOT NULL",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_release",Integer.class)).isEqualTo(1);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Original closed TEMP columns and references preserved through migration, validation and repeat");
    }
    private JdbcTemplate connect(E2aCrossSourceProfile profile) {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(profile.url,profile.user,System.getenv("E2A_CROSS_PASSWORD")));
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo(profile.database);
        assertThat(jdbc.queryForObject("SELECT @@port",Integer.class)).isEqualTo(19330);
        assertThat(jdbc.queryForObject("SELECT @@datadir",String.class).replace('\\','/'))
            .isEqualToIgnoringCase(profile.attempt.resolve("data").toString().replace('\\','/')+"/");
        assertThat(Files.isRegularFile(profile.attempt.resolve("open-temp-closed-source-sha256.txt"))).isTrue();
        assertThat(jdbc.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history",Integer.class)).isEqualTo(88);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_release",Integer.class)).isEqualTo(1);
        return jdbc;
    }
}
