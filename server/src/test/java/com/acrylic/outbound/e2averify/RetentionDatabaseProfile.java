package com.acrylic.outbound.e2averify;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Separate one-shot C-drive fixture; does not weaken the existing E2a workspace guards. */
final class RetentionDatabaseProfile {
    final Path attempt;
    final String database;
    private final String user;
    private final String password;
    private static final Path MYSQL = Path.of("C:/Program Files/MySQL/MySQL Server 8.0/bin");

    RetentionDatabaseProfile() throws Exception {
        String run = System.getenv("B4_RETENTION_RUN");
        require(run != null && run.matches("[1-9][0-9]{0,3}"), "Invalid retention attempt");
        Path parent = Path.of(System.getenv("LOCALAPPDATA"), "acrylic-pda-outbound-isolated");
        attempt = parent.resolve("retention-20261006-" + run);
        require(parent.toRealPath().equals(parent.toAbsolutePath()), "Redirected isolated root");
        require(attempt.toRealPath().equals(attempt.toAbsolutePath()), "Redirected attempt");
        require(Files.isDirectory(attempt.resolve("data")), "Missing new datadir");
        require(!Files.exists(attempt.resolve("test-started.marker")), "Attempt already consumed");
        require(Path.of("").toRealPath().equals(Path.of("D:/acrylic-pda-outbound/server").toRealPath()), "Wrong code tree");
        require(Files.getFileStore(attempt).getUsableSpace() > 20L * 1024 * 1024 * 1024, "Insufficient isolated disk space");
        database = "b4_retention_20261006_" + run;
        user = "b4_retention_" + run;
        password = System.getenv("B4_RETENTION_PASSWORD");
        require(password != null && password.matches("[a-f0-9]{64}"), "Missing isolated credential");
        for (String key : new String[]{"OUTBOUND_DB_RESET", "OUTBOUND_DB_REPAIR", "E2A_MIGRATION_RESET"})
            require(!"true".equals(System.getenv(key)), "Reset/repair forbidden");
    }

    JdbcTemplate connect(String suffix) {
        require(suffix.isEmpty() || suffix.equals("_restore"), "Unexpected schema suffix");
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:19331/" + database + suffix
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", user, password));
        String datadir = jdbc.queryForObject("SELECT @@datadir", String.class).replace('\\', '/');
        require(datadir.equalsIgnoreCase(attempt.resolve("data").toString().replace('\\', '/') + "/"), "Datadir mismatch");
        require((database + suffix).equals(jdbc.queryForObject("SELECT DATABASE()", String.class)), "Schema mismatch");
        require((user + "@127.0.0.1").equals(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)), "Account mismatch");
        require(jdbc.queryForObject("SELECT @@port", Integer.class) == 19331, "Port mismatch");
        require(jdbc.queryForObject("SELECT @@log_bin", Integer.class) == 1, "Binlog required");
        require(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()",
            Integer.class) == 0, "Refusing nonempty test schema");
        return jdbc;
    }

    void migrate(JdbcTemplate jdbc, String target) {
        Flyway.configure().dataSource(jdbc.getDataSource()).target(target).cleanDisabled(true).load().migrate();
    }

    void backupAndRestore() throws Exception {
        Path backup = attempt.resolve("before-upgrade.sql");
        run(new ProcessBuilder(MYSQL.resolve("mysqldump.exe").toString(), defaults(), "--single-transaction",
            "--no-tablespaces", "--set-gtid-purged=OFF", "--routines", "--triggers", database)
            .redirectOutput(backup.toFile()).redirectError(attempt.resolve("backup.err.log").toFile()));
        require(Files.size(backup) > 0, "Empty pre-upgrade backup");
        run(new ProcessBuilder(MYSQL.resolve("mysql.exe").toString(), defaults(), database + "_restore")
            .redirectInput(backup.toFile()).redirectOutput(attempt.resolve("restore.out.log").toFile())
            .redirectError(attempt.resolve("restore.err.log").toFile()));
    }

    private String defaults() { return "--defaults-file=" + attempt.resolve("private/client.cnf"); }
    private void run(ProcessBuilder builder) throws Exception {
        Process process = builder.start();
        require(process.waitFor(60, TimeUnit.SECONDS), "Database backup/restore still running; inspect process");
        require(process.exitValue() == 0, "Database backup/restore failed; retain logs");
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
