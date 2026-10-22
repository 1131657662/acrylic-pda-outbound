package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.assertThat;

/** Real MySQL identity observations over explicitly MODEL closed-admission rows, not a restore. */
class RecoveryAdmissionDatabaseIdentityIT {
    private static final List<String> TABLES = List.of("server_state", "recovery_installation_binding",
        "recovery_control_epoch", "recovery_control_projection", "recovery_normal_admission");

    @Test void copiedAdmissionRowsCannotAdmitAnotherPhysicalSchema() throws Exception {
        EffectRealEnvironment.requireEnvironment();
        Path output = Path.of(System.getenv("B11_EFFECT_EVIDENCE_DIR"));
        Path restored = Files.createDirectory(output.resolve("model-business"));
        var fixture = RecoveryControlFixture.retainedAt(output.resolve("model-control"), restored);
        fixture.toReleaseReady();
        RecoveryControlFixture.normal(fixture, EffectRealEnvironment.USER).write();
        migrate();
        try (var connection = EffectRealEnvironment.open(false)) {
            var source = new SingleConnectionDataSource(connection, true);
            var jdbc = new JdbcTemplate(source);
            seed(jdbc, fixture);
            String original = connection.getCatalog(), copied = copy(jdbc, original);
            var files = fileHashes(output.resolve("model-control"));
            var rows = rows(jdbc);
            var admitted = observe(jdbc, source, fixture, "matched");
            assertThat(admitted.admitted()).isTrue();
            assertThat(admitted.gaps()).isEmpty();
            assertThat(rows(jdbc)).isEqualTo(rows);
            copied(jdbc, connection, source, fixture, copied, rows);
            connection.setCatalog(original);
            assertThat(rows(jdbc)).isEqualTo(rows);
            assertThat(fileHashes(output.resolve("model-control"))).isEqualTo(files);
            EffectRealEnvironment.record("REAL|conservation|tables=" + rows.size() + "|externalFiles=" + files.size());
        }
    }

    private static void migrate() {
        Flyway.configure().dataSource(System.getenv("OUTBOUND_DB_URL"), System.getenv("OUTBOUND_DB_USER"),
            System.getenv("OUTBOUND_DB_PASSWORD")).load().migrate();
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
        jdbc.update("""
            INSERT INTO recovery_control_epoch VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),2,4,UNHEX(?))
            """, installation, epoch, control.recordSha256());
        jdbc.update("""
            INSERT INTO recovery_control_projection
                (installation_id,control_revision,record_sha256,previous_record_sha256,epoch_id,numeric_epoch,control_phase)
            VALUES(UUID_TO_BIN(?),4,UNHEX(?),UNHEX(?),UUID_TO_BIN(?),2,'RELEASE_READY')
            """, installation, control.recordSha256(), control.previousRecordSha256(), epoch);
        jdbc.update("""
            INSERT INTO recovery_normal_admission
                (id,format_version,installation_id,authority_revision,control_revision,control_record_sha256,
                 previous_record_sha256,intent_sha256,numeric_epoch,operator_id)
            VALUES(1,1,UUID_TO_BIN(?),4,4,UNHEX(?),UNHEX(?),UNHEX(?),2,'MODEL-operator')
            """, installation, control.recordSha256(), control.previousRecordSha256(), intent.intentSha256());
        jdbc.update("UPDATE server_state SET recovery_epoch=2,service_mode='NORMAL' WHERE id=1");
        EffectRealEnvironment.record("MODEL|closed-registration|installation=" + installation
            + "|schema=" + EffectRealEnvironment.USER + "|baseline=1|numeric=2|java-fixture|not-complete-recovery");
    }

    private static String copy(JdbcTemplate jdbc, String original) throws Exception {
        String copied = original + "_copy";
        assertThat(copied).matches("p5_lost_[0-9]+_copy");
        for (String table : TABLES) {
            jdbc.execute("CREATE TABLE `" + copied + "`.`" + table + "` LIKE `" + original + "`.`" + table + "`");
            jdbc.update("INSERT INTO `" + copied + "`.`" + table + "` SELECT * FROM `" + original + "`.`" + table + "`");
        }
        EffectRealEnvironment.record("MODEL|copied-tables|schema=" + copied + "|same-rows|LIKE-without-FK-or-triggers");
        return copied;
    }

    private static void copied(JdbcTemplate jdbc, Connection connection, SingleConnectionDataSource source,
            RecoveryControlFixture fixture, String copied, Map<String, String> originalRows) throws Exception {
        connection.setCatalog(copied);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(copied);
        assertThat(rows(jdbc)).isEqualTo(originalRows);
        var denied = observe(jdbc, source, fixture, "copied-schema");
        assertThat(denied.admitted()).isFalse();
        assertThat(denied.gaps()).containsExactly("DATABASE_SCHEMA");
        assertThat(rows(jdbc)).isEqualTo(originalRows);
    }

    private static RecoveryAdmissionVerifier.Result observe(JdbcTemplate jdbc, SingleConnectionDataSource source,
            RecoveryControlFixture fixture, String label) throws Exception {
        var counters = counters(jdbc);
        var binlog = jdbc.queryForList("SHOW MASTER STATUS");
        var result = RecoveryAdmissionVerifier.of(new RecoveryAdmissionQueries(source), fixture.reader(),
            fixture.checkpoint, fixture.location(), 1).verify();
        var after = counters(jdbc);
        assertThat(after.get("Com_select") - counters.get("Com_select")).isEqualTo(1);
        for (String command : List.of("Com_insert", "Com_update", "Com_delete", "Com_replace"))
            assertThat(after.get(command)).as(command).isEqualTo(counters.get(command));
        assertThat(jdbc.queryForList("SHOW MASTER STATUS")).isEqualTo(binlog);
        assertThat(result.ordinaryWritesAllowed()).isFalse();
        assertThat(result.localDeletionAllowed()).isFalse();
        EffectRealEnvironment.record("REAL|observe|" + label + "|schema=" + jdbc.queryForObject("SELECT DATABASE()", String.class)
            + "|admitted=" + result.admitted() + "|gaps=" + result.gaps() + "|selectDelta=1|DML=0|binlogUnchanged");
        return result;
    }

    private static Map<String, Long> counters(JdbcTemplate jdbc) {
        return jdbc.query("SHOW SESSION STATUS WHERE Variable_name IN ('Com_select','Com_insert','Com_update','Com_delete','Com_replace')",
            rs -> {
                var result = new TreeMap<String, Long>();
                while (rs.next()) result.put(rs.getString(1), rs.getLong(2));
                return result;
            });
    }

    private static Map<String, String> rows(JdbcTemplate jdbc) throws Exception {
        var result = new TreeMap<String, String>();
        for (String table : TABLES) {
            String text = jdbc.query("SELECT * FROM `" + table + "`", rs -> {
                var values = new ArrayList<String>();
                while (rs.next()) {
                    var row = new ArrayList<String>();
                    for (int column = 1; column <= rs.getMetaData().getColumnCount(); column++) {
                        byte[] bytes = rs.getBytes(column);
                        row.add(bytes == null ? "NULL" : HexFormat.of().formatHex(bytes));
                    }
                    values.add(String.join(":", row));
                }
                Collections.sort(values);
                return String.join("\n", values);
            });
            result.put(table, sha(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        EffectRealEnvironment.record("REAL|row-hashes|" + result);
        return result;
    }

    private static Map<String, String> fileHashes(Path root) throws Exception {
        var result = new TreeMap<String, String>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList())
                result.put(root.relativize(file).toString(), sha(Files.readAllBytes(file)));
        }
        return result;
    }

    private static String sha(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
