package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import static org.assertj.core.api.Assertions.*;

/** Staged pre-upgrade backup: import only into the guarded empty companion, never over the source. */
final class E2aUpgradeCheckpoint implements AutoCloseable {
    private final E2aCrossSourceProfile profile;
    private final E2aArchiveSnapshotRestore snapshots;
    private final Path backup;
    private HikariDataSource restorePool;

    E2aUpgradeCheckpoint(E2aCrossSourceProfile profile, JdbcTemplate source) {
        this.profile = profile;
        snapshots = new E2aArchiveSnapshotRestore(profile, source);
        backup = profile.attempt.resolve("upgrade-checkpoint");
    }

    ObjectNode capture(JdbcTemplate jdbc, Path storage) throws Exception {
        return snapshots.snapshot(jdbc, storage);
    }

    void save(JdbcTemplate source) throws Exception {
        Files.createDirectory(backup);
        var before = capture(source, profile.attempt.resolve("storage"));
        copyTree(profile.attempt.resolve("storage"), backup.resolve("storage"));
        Files.createDirectory(backup.resolve("config"));
        for (String name : List.of("server-private.der", "server-public.der"))
            Files.copy(profile.attempt.resolve("private/" + name), backup.resolve("config/" + name));
        var args = command("mysqldump");
        args.addAll(List.of("--single-transaction", "--skip-lock-tables", "--no-tablespaces", "--set-gtid-purged=OFF",
            "--hex-blob", "--routines", "--triggers", "--events", "--skip-add-drop-table", profile.database));
        run(args, backup.resolve("database.sql"), null);
        assertThat(Files.size(backup.resolve("database.sql"))).isPositive();
        assertThat(capture(source, profile.attempt.resolve("storage"))).isEqualTo(before);
        Files.writeString(backup.resolve("facts.json"), before.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    JdbcTemplate restore() throws Exception {
        var target = restoreTarget();
        requireEmptyTarget(target);
        Path restored = profile.attempt.resolve("upgrade-restored");
        Files.createDirectory(restored);
        copyTree(backup.resolve("storage"), restored.resolve("storage"));
        copyTree(backup.resolve("config"), restored.resolve("config"));
        var args = command("mysql");
        args.addAll(List.of("--binary-mode", "--default-character-set=utf8mb4", profile.database + "_restore"));
        requireEmptyTarget(target);
        run(args, restored.resolve("import.stdout"), backup.resolve("database.sql"));
        assertThat(capture(target, restored.resolve("storage"))).isEqualTo(
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(backup.resolve("facts.json").toFile()));
        for (String name : List.of("server-private.der", "server-public.der"))
            assertThat(Files.readAllBytes(restored.resolve("config/" + name)))
                .isEqualTo(Files.readAllBytes(profile.attempt.resolve("private/" + name)));
        return target;
    }

    @Override public void close() {
        if (restorePool != null) restorePool.close();
    }

    private JdbcTemplate restoreTarget() {
        if (restorePool == null) {
            var settings = new HikariConfig();
            settings.setJdbcUrl(profile.url.replace("/" + profile.database + "?", "/" + profile.database + "_restore?"));
            settings.setUsername(profile.user);
            settings.setPassword(System.getenv("E2A_CROSS_PASSWORD"));
            settings.setMaximumPoolSize(2);
            settings.setMinimumIdle(0);
            settings.setConnectionTimeout(30_000);
            restorePool = new HikariDataSource(settings);
        }
        return new JdbcTemplate(restorePool);
    }

    private void requireEmptyTarget(JdbcTemplate target) {
        assertThat(target.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(profile.database + "_restore");
        assertThat(target.queryForObject("SELECT CURRENT_USER()", String.class)).isEqualTo(profile.user + "@127.0.0.1");
        assertThat(target.queryForObject("SELECT @@port", Integer.class)).isEqualTo(E2aTestPorts.database());
        assertThat(Path.of(target.queryForObject("SELECT @@datadir", String.class)).toAbsolutePath().normalize())
            .isEqualTo(profile.attempt.resolve("data").toAbsolutePath().normalize());
        assertThat(target.queryForList("SHOW TABLES")).as("restore target must be empty").isEmpty();
    }

    private void copyTree(Path source, Path target) throws Exception {
        var manifest = E2aSnapshotFiles.capture(source, profile.attempt);
        Files.createDirectory(target);
        var names = manifest.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            Path destination = target.resolve(name).normalize();
            assertThat(destination.startsWith(target)).isTrue();
            Files.createDirectories(destination.getParent());
            Files.copy(source.resolve(name), destination);
        }
        assertThat(E2aSnapshotFiles.capture(target, profile.attempt)).isEqualTo(manifest);
    }

    private List<String> command(String executable) {
        return new ArrayList<>(List.of("C:/Program Files/MySQL/MySQL Server 8.0/bin/" + executable + ".exe",
            "--defaults-file=" + profile.attempt.resolve("private/client.cnf"), "--protocol=TCP", "--host=127.0.0.1",
            "--port=" + E2aTestPorts.database()));
    }

    private void run(List<String> command, Path output, Path input) throws Exception {
        assertThat(Files.exists(output)).isFalse();
        var builder = new ProcessBuilder(command);
        builder.environment().remove("MYSQL_PWD");
        builder.environment().put("MYSQL_TEST_LOGIN_FILE", profile.attempt.resolve("private/no-login-file").toString());
        builder.redirectOutput(output.toFile());
        builder.redirectError(Path.of(output + ".stderr").toFile());
        if (input != null) builder.redirectInput(input.toFile());
        var process = builder.start();
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).as("checkpoint CLI completion").isTrue();
        assertThat(process.exitValue()).as("checkpoint CLI exit; see protected stderr").isZero();
    }
}
