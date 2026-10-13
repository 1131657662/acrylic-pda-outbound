package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/** No physical application signature can be invented for a restored historical submission. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aOpenFormalQuarantineHistoryIT {
    @Test void historicalCompletedFormalOriginalsSurviveWithoutApplicationBackfill() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=new JdbcTemplate(new DriverManagerDataSource(profile.url,profile.user,System.getenv("E2A_CROSS_PASSWORD")));
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo(profile.database);
        assertThat(jdbc.queryForObject("SELECT @@port",Integer.class)).isEqualTo(19330);
        assertThat(jdbc.queryForObject("SELECT @@datadir",String.class).replace('\\','/'))
            .isEqualToIgnoringCase(profile.attempt.resolve("data").toString().replace('\\','/')+"/");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_origin WHERE formal_disposition_id IS NOT NULL",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history",Integer.class)).isEqualTo(90);
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        var originals=new CorrectionAccountingMigrationOriginals(jdbc,profile.attempt);var before=originals.snapshot("open-formal-history-before");
        var migration=Flyway.configure().dataSource(jdbc.getDataSource()).load();migration.migrate();migration.validate();migration.migrate();
        assertThat(originals.snapshot("open-formal-history-after")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM formal_disposition_application",Integer.class)).isZero();
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Original completed FORMAL full projections retained; no historical application signatures fabricated");
    }
}
