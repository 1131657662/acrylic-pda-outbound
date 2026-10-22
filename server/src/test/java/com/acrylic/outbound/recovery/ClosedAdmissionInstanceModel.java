package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic closed-registration rows only; copying these five tables is not a complete restore. */
final class ClosedAdmissionInstanceModel {
    static final List<String> TABLES = List.of("server_state", "recovery_installation_binding",
        "recovery_control_epoch", "recovery_control_projection", "recovery_normal_admission");

    static RecoveryControlFixture create(JdbcTemplate jdbc, Path output) throws Exception {
        Path business = Files.createDirectory(output.resolve("model-business"));
        var fixture = RecoveryControlFixture.retainedAt(output.resolve("model-control"), business);
        fixture.toReleaseReady();
        RecoveryControlFixture.normal(fixture, EffectRealEnvironment.USER).write();
        seed(jdbc, fixture);
        return fixture;
    }

    private static void seed(JdbcTemplate jdbc, RecoveryControlFixture fixture) throws Exception {
        var control = fixture.read();
        var intent = RecoveryNormalIntent.load(fixture.location(), fixture.root, 1).intent();
        String installation = fixture.installation.toString(), epoch = control.epochId().toString();
        jdbc.update("""
            INSERT INTO recovery_installation_binding
                (id,installation_id,mysql_server_uuid,database_schema,baseline_numeric_epoch,initial_control_sha256)
            VALUES(1,UUID_TO_BIN(?),UUID_TO_BIN(@@server_uuid),DATABASE(),1,UNHEX(?))
            """, installation, fixture.latest.path("recordSha256").asText());
        jdbc.update("INSERT INTO recovery_control_epoch VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),2,4,UNHEX(?))",
            installation, epoch, control.recordSha256());
        jdbc.update("""
            INSERT INTO recovery_control_projection
                (installation_id,control_revision,record_sha256,previous_record_sha256,epoch_id,numeric_epoch,control_phase)
            VALUES(UUID_TO_BIN(?),4,UNHEX(?),UNHEX(?),UUID_TO_BIN(?),2,'RELEASE_READY')
            """, installation, control.recordSha256(), control.previousRecordSha256(), epoch);
        jdbc.update("""
            INSERT INTO recovery_normal_admission
                (id,format_version,installation_id,authority_revision,control_revision,control_record_sha256,
                 previous_record_sha256,intent_sha256,numeric_epoch,operator_id)
            VALUES(1,1,UUID_TO_BIN(?),4,4,UNHEX(?),UNHEX(?),UNHEX(?),2,'MODEL-cross-instance')
            """, installation, control.recordSha256(), control.previousRecordSha256(), intent.intentSha256());
        jdbc.update("UPDATE server_state SET recovery_epoch=2,service_mode='NORMAL' WHERE id=1");
        EffectRealEnvironment.record("MODEL|closed-registration|installation=" + installation
            + "|schema=" + EffectRealEnvironment.USER + "|fixture|not-complete-recovery");
    }

    static void copy(JdbcTemplate source, JdbcTemplate target) {
        // Only the pristine target's generated singleton is replaced, never application data.
        assertThat(target.queryForObject("SELECT COUNT(*) FROM server_state", Long.class)).isEqualTo(1L);
        for (String table : TABLES.subList(1, TABLES.size()))
            assertThat(target.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Long.class)).isZero();
        target.update("DELETE FROM server_state WHERE id=1");
        for (String table : TABLES) copyTable(source, target, table);
    }

    private static void copyTable(JdbcTemplate source, JdbcTemplate target, String table) {
        for (var row : source.queryForList("SELECT * FROM `" + table + "`")) {
            var names = new ArrayList<>(row.keySet());
            for (String name : names) assertThat(name).matches("[a-z0-9_]+");
            String columns = "`" + String.join("`,`", names) + "`";
            String parameters = String.join(",", Collections.nCopies(names.size(), "?"));
            target.update("INSERT INTO `" + table + "` (" + columns + ") VALUES (" + parameters + ")",
                names.stream().map(row::get).toArray());
        }
    }
}
