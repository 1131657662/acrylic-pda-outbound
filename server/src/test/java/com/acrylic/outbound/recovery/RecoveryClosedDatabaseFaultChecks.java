package com.acrylic.outbound.recovery;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/** Separate preallocated copies retain every refusal input without altering the source database. */
final class RecoveryClosedDatabaseFaultChecks {
    static void verify(JdbcTemplate original, RecoveryClosedAuthority authority, RecoveryPinnedSource source) {
        for (String suffix : List.of("copy", "instance", "missing", "installation", "conflict")) {
            var copy = copy(original, suffix);
            if (suffix.equals("instance")) copy.update("UPDATE recovery_installation_binding SET mysql_server_uuid=UUID_TO_BIN(UUID())");
            if (suffix.equals("installation")) copy.update("UPDATE recovery_installation_binding SET installation_id=UUID_TO_BIN(UUID())");
            if (suffix.equals("conflict")) copy.update("UPDATE recovery_control_projection SET record_sha256=UNHEX(REPEAT('f',64)) WHERE control_revision=2");
            var transaction = new RecoveryClosedTransaction(copy.getDataSource(), authority);
            assertThatThrownBy(() -> transaction.execute(source, status -> { throw new AssertionError("Invalid projection callback"); }))
                .isInstanceOf(RecoveryControlException.class);
            assertThat(copy.queryForObject("SELECT COUNT(*) FROM closed_recovery_probe", Integer.class)).isZero();
        }
    }

    private static JdbcTemplate copy(JdbcTemplate original, String suffix) {
        var dataSource = (DriverManagerDataSource)original.getDataSource();
        String schema = original.queryForObject("SELECT DATABASE()", String.class);
        assertThat(schema).matches("acrylic_e2a_cross_20260925_[1-9][0-9]{0,3}");
        String target = schema + "_closed_" + suffix;
        var copy = new JdbcTemplate(new DriverManagerDataSource(dataSource.getUrl().replace("/" + schema + "?", "/" + target + "?"),
            dataSource.getUsername(), dataSource.getPassword()));
        assertThat(copy.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class)).isZero();
        for (String table : List.of("server_state", "recovery_installation_binding", "recovery_control_epoch", "recovery_control_projection", "closed_recovery_probe")) {
            copy.execute("CREATE TABLE " + table + " LIKE `" + schema + "`." + table);
            if (!table.equals("closed_recovery_probe") && !(suffix.equals("missing") && table.equals("recovery_control_projection")))
                copy.update("INSERT INTO " + table + " SELECT * FROM `" + schema + "`." + table);
        }
        if (!suffix.equals("copy")) copy.update("UPDATE recovery_installation_binding SET database_schema=?", target);
        return copy;
    }
}
