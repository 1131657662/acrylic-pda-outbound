package com.acrylic.outbound.recovery;

import java.util.*;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/** Dedicated negative-case schemas, preallocated by the isolated runner; originals are never changed. */
final class RecoveryProjectionDatabases {
    private final DriverManagerDataSource source;
    private final String original;

    RecoveryProjectionDatabases(JdbcTemplate jdbc) {
        source = (DriverManagerDataSource) jdbc.getDataSource();
        original = jdbc.queryForObject("SELECT DATABASE()", String.class);
        assertThat(original).matches("acrylic_e2a_cross_20260925_[1-9][0-9]{0,2}");
    }

    JdbcTemplate empty(String suffix) {
        assertThat(suffix).isIn("copy", "conflict", "missing", "gap", "overflow", "rollback", "instance", "skip", "mapping");
        String target = original + "_" + suffix;
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(source.getUrl().replace("/" + original + "?", "/" + target + "?"),
            source.getUsername(), source.getPassword()));
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(target);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class)).isZero();
        jdbc.execute("CREATE TABLE server_state LIKE " + name(original) + ".server_state");
        jdbc.update("INSERT INTO server_state SELECT * FROM " + name(original) + ".server_state");
        Flyway.configure().dataSource(jdbc.getDataSource()).baselineOnMigrate(true).baselineVersion("38").target("39").load().migrate();
        return jdbc;
    }

    JdbcTemplate copy(String suffix) {
        var jdbc = empty(suffix);
        String schema = suffix.equals("copy") ? original : original + "_" + suffix;
        String hash = suffix.equals("conflict") ? "UNHEX(REPEAT('f',64))" : "record_sha256";
        jdbc.update("INSERT INTO recovery_installation_binding SELECT id,installation_id,"
            + (suffix.equals("instance") ? "UUID_TO_BIN(UUID())" : "mysql_server_uuid")
            + ",?,baseline_numeric_epoch,initial_control_sha256,registered_at FROM " + name(original) + ".recovery_installation_binding", schema);
        jdbc.update("INSERT INTO recovery_control_epoch SELECT installation_id,epoch_id,numeric_epoch,first_control_revision,"
            + (suffix.equals("mapping") ? "UNHEX(REPEAT('e',64))" : "first_control_sha256")
            + " FROM " + name(original) + ".recovery_control_epoch");
        if (!suffix.equals("missing")) {
            jdbc.update("INSERT INTO recovery_control_projection SELECT installation_id,control_revision," + hash
                + ",previous_record_sha256,epoch_id,numeric_epoch,control_phase,projected_at FROM "
                + name(original) + ".recovery_control_projection" + (suffix.equals("gap") ? " WHERE control_revision<>2" : ""));
        }
        return jdbc;
    }

    private String name(String value) {
        assertThat(value).matches("[a-z0-9_]+"); return "`" + value + "`";
    }
}
