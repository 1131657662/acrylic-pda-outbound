package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

/** Restored completed TEMP/PC references are consumed without inventing FORMAL approval bytes. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aFormalQuarantineHistoryIT {
    @Test void completedOriginalsSurviveFormalExtensionWithoutBackfill() throws Exception {
        var profile=new E2aCrossSourceProfile(); var jdbc=connect(profile);
        String kind=System.getenv("FORMAL_HISTORY_KIND"); assertThat(kind).isIn("TEMP","PC");
        String column=kind.equals("PC")?"pc_disposition_id":"disposition_id";
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_origin WHERE "+column+" IS NOT NULL",Integer.class)).isEqualTo(1);
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        var originals=new CorrectionAccountingMigrationOriginals(jdbc,profile.attempt);
        var before=originals.snapshot("formal-extension-history-before");
        var migration=Flyway.configure().dataSource(jdbc.getDataSource()).load();
        migration.migrate(); migration.validate();
        assertThat(originals.snapshot("formal-extension-history-after")).isEqualTo(before);
        migration.migrate(); assertThat(originals.snapshot("formal-extension-history-repeat")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM formal_disposition_receipt",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_origin WHERE formal_disposition_id IS NOT NULL",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_release",Integer.class)).isEqualTo(1);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Preserved "+kind+" original references, full projections and triggers; FORMAL migration/validate/repeat without fabricated approvals");
    }
    private JdbcTemplate connect(E2aCrossSourceProfile profile) {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(profile.url,profile.user,System.getenv("E2A_CROSS_PASSWORD")));
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo(profile.database);
        assertThat(jdbc.queryForObject("SELECT @@port",Integer.class)).isEqualTo(19330);
        assertThat(jdbc.queryForObject("SELECT @@datadir",String.class).replace('\\','/'))
            .isEqualToIgnoringCase(profile.attempt.resolve("data").toString().replace('\\','/')+"/");
        assertThat(Files.isRegularFile(profile.attempt.resolve("formal-extension-source-sha256.txt"))).isTrue();
        assertThat(jdbc.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history",Integer.class)).isEqualTo(89);
        return jdbc;
    }
}
