package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.assertThat;

/** Records row/file hashes and proves that an individual verifier invocation stays read-only. */
final class ClosedAdmissionIdentityObservation {
    static RecoveryAdmissionVerifier.Result observe(SingleConnectionDataSource source,
            RecoveryControlFixture fixture, String label) throws Exception {
        var jdbc = new JdbcTemplate(source);
        var before = counters(jdbc);
        var binlog = jdbc.queryForList("SHOW MASTER STATUS");
        var result = RecoveryAdmissionVerifier.of(new RecoveryAdmissionQueries(source), fixture.reader(),
            fixture.checkpoint, fixture.location(), 1).verify();
        var after = counters(jdbc);
        assertThat(after.get("Com_select") - before.get("Com_select")).isEqualTo(1);
        for (String command : List.of("Com_insert", "Com_update", "Com_delete", "Com_replace"))
            assertThat(after.get(command)).as(command).isEqualTo(before.get(command));
        assertThat(jdbc.queryForList("SHOW MASTER STATUS")).isEqualTo(binlog);
        assertThat(result.ordinaryWritesAllowed()).isFalse();
        assertThat(result.localDeletionAllowed()).isFalse();
        EffectRealEnvironment.record("REAL|observe|" + label + "|identity="
            + jdbc.queryForList("SELECT @@server_uuid AS uuid,DATABASE() AS schema_name")
            + "|admitted=" + result.admitted() + "|gaps=" + result.gaps() + "|selectDelta=1|DML=0|binlogUnchanged");
        return result;
    }

    static Map<String, String> rows(JdbcTemplate jdbc) throws Exception {
        var hashes = new TreeMap<String, String>();
        for (String table : ClosedAdmissionInstanceModel.TABLES) {
            String values = jdbc.query("SELECT * FROM `" + table + "`", rs -> {
                var rows = new ArrayList<String>();
                while (rs.next()) {
                    var cells = new ArrayList<String>();
                    for (int column = 1; column <= rs.getMetaData().getColumnCount(); column++) {
                        byte[] bytes = rs.getBytes(column);
                        cells.add(bytes == null ? "NULL" : HexFormat.of().formatHex(bytes));
                    }
                    rows.add(String.join(":", cells));
                }
                Collections.sort(rows);
                return String.join("\n", rows);
            });
            hashes.put(table, sha(values.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        EffectRealEnvironment.record("REAL|row-hashes|" + hashes);
        return hashes;
    }

    static Map<String, String> files(Path root) throws Exception {
        var hashes = new TreeMap<String, String>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList())
                hashes.put(root.relativize(file).toString(), sha(Files.readAllBytes(file)));
        }
        return hashes;
    }

    private static Map<String, Long> counters(JdbcTemplate jdbc) {
        return jdbc.query("SHOW SESSION STATUS WHERE Variable_name IN ('Com_select','Com_insert','Com_update','Com_delete','Com_replace')",
            rs -> {
                var result = new TreeMap<String, Long>();
                while (rs.next()) result.put(rs.getString(1), rs.getLong(2));
                return result;
            });
    }

    static String sha(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
