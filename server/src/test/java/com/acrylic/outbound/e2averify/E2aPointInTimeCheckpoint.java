package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import static org.assertj.core.api.Assertions.*;

/** Immutable checkpoint imported only into a checked empty companion on the same dedicated lab. */
final class E2aPointInTimeCheckpoint implements AutoCloseable {
    final String schema, url;
    final Path directory;
    private final E2aCrossSourceProfile profile;
    private final E2aPointInTimeCommands commands;
    private final E2aArchiveSnapshotRestore snapshots;
    private final ObjectNode before;
    private final String databaseCollation;
    private final String file, dumpPin;
    private final long position;
    private HikariDataSource targetPool;

    E2aPointInTimeCheckpoint(E2aCrossSourceProfile profile, JdbcTemplate source, String name, String suffix) throws Exception {
        this.profile = profile; commands = new E2aPointInTimeCommands(profile);
        assertThat(List.of("_copy", "_conflict", "_missing", "_gap", "_restore")).contains(suffix);
        assertThat(name).matches("[a-z-]+"); schema = profile.database + suffix;
        url = profile.url.replace("/" + profile.database + "?", "/" + schema + "?");
        directory = profile.attempt.resolve("checkpoints/" + name); Files.createDirectories(directory);
        snapshots = new E2aArchiveSnapshotRestore(profile, source);
        before = snapshots.snapshot(source, profile.attempt.resolve("storage"));
        databaseCollation = source.queryForObject(
            "SELECT DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=DATABASE()",
            String.class);
        assertThat(databaseCollation).matches("[A-Za-z0-9_]+");
        var revision = source.queryForMap("SHOW MASTER STATUS");
        file = revision.get("File").toString(); position = ((Number) revision.get("Position")).longValue();
        commands.dump(profile.database, directory.resolve("database.sql"));
        E2aPointInTimeCommands.copyTree(profile.attempt.resolve("storage"), directory.resolve("storage"), profile.attempt);
        E2aPointInTimeCommands.copyTree(profile.attempt.resolve("private-signing"), directory.resolve("config"), profile.attempt);
        assertThat(snapshots.snapshot(source, profile.attempt.resolve("storage"))).isEqualTo(before);
        assertThat(source.queryForMap("SHOW MASTER STATUS")).isEqualTo(revision);
        dumpPin = sha(directory.resolve("database.sql"));
        var metadata = SubmissionJson.object().put("schema", schema).put("binlogFile", file)
            .put("position", position).put("dumpSha256", dumpPin);
        Files.writeString(directory.resolve("metadata.json"), metadata.toPrettyString(), StandardOpenOption.CREATE_NEW);
        Files.writeString(directory.resolve("before.json"), before.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    JdbcTemplate rebuild(Path binlog, String binlogPin, String endFile, long end, JsonNode expected) throws Exception {
        assertThat(file).isEqualTo(endFile); assertThat(sha(binlog)).isEqualTo(binlogPin);
        assertThat(sha(directory.resolve("database.sql"))).isEqualTo(dumpPin);
        targetPool = pool(profile, url);
        var target = new JdbcTemplate(targetPool);
        requireEmpty(target);
        target.execute("ALTER DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE " + databaseCollation);
        requireEmpty(target);
        commands.restore(schema, directory.resolve("database.sql"), directory.resolve("import.stdout"));
        assertThat(snapshots.snapshot(target, directory.resolve("storage"))).isEqualTo(before);
        assertThatThrownBy(() -> requireEmpty(target)).isInstanceOf(AssertionError.class);
        assertThat(snapshots.snapshot(target, directory.resolve("storage"))).isEqualTo(before);
        commands.decode(schema, binlog, position, end, directory.resolve("replay.sql"));
        commands.restore(schema, directory.resolve("replay.sql"), directory.resolve("replay.stdout"));
        E2aPointInTimeCommands.copyTree(profile.attempt.resolve("final-storage"), directory.resolve("final-storage"), profile.attempt);
        var actual = snapshots.snapshot(target, directory.resolve("final-storage"));
        assertThat(actual).isEqualTo(expected);
        Files.writeString(directory.resolve("rebuilt.json"), actual.toPrettyString(), StandardOpenOption.CREATE_NEW);
        assertThat(sha(binlog)).isEqualTo(binlogPin);
        assertThat(sha(directory.resolve("database.sql"))).isEqualTo(dumpPin);
        return target;
    }

    private void requireEmpty(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(schema);
        assertThat(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)).isEqualTo(profile.user + "@127.0.0.1");
        assertThat(jdbc.queryForObject("SELECT @@port", Integer.class)).isEqualTo(E2aTestPorts.database());
        assertThat(Path.of(jdbc.queryForObject("SELECT @@datadir", String.class)).toAbsolutePath().normalize())
            .isEqualTo(profile.attempt.resolve("data").toAbsolutePath().normalize());
        assertThat(jdbc.queryForList("SHOW TABLES")).as("restore target must be empty").isEmpty();
    }

    static JsonNode triggers(JdbcTemplate jdbc) {
        return SubmissionJson.object().set("triggers", SubmissionJson.object().arrayNode().addAll(
            jdbc.query("SELECT TRIGGER_NAME,EVENT_MANIPULATION,ACTION_STATEMENT,ACTION_TIMING,ACTION_ORDER,DEFINER,"
                + "SQL_MODE,CHARACTER_SET_CLIENT,COLLATION_CONNECTION,DATABASE_COLLATION FROM information_schema.TRIGGERS "
                + "WHERE TRIGGER_SCHEMA=DATABASE() ORDER BY TRIGGER_NAME", (rs, index) -> {
                    var row = SubmissionJson.object();
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
                        row.put(rs.getMetaData().getColumnLabel(i), rs.getString(i));
                    return row;
                })));
    }

    static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    static HikariDataSource pool(E2aCrossSourceProfile profile, String url) {
        var settings = new HikariConfig();
        settings.setJdbcUrl(url); settings.setUsername(profile.user);
        settings.setPassword(System.getenv("E2A_CROSS_PASSWORD"));
        settings.setMaximumPoolSize(2); settings.setMinimumIdle(0); settings.setConnectionTimeout(30_000);
        return new HikariDataSource(settings);
    }

    @Override public void close() { if (targetPool != null) targetPool.close(); }
}
