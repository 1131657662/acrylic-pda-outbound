package com.acrylic.outbound.e2averify;

import java.nio.file.Path;
import java.util.Map;
import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.JsonNode;

/** Opt-in entry point for future target-34/35 execution; disabled without the full guard. */
public final class E2aMigrationRunner {
    private E2aMigrationRunner() { }

    public static void main(String[] args) throws Exception {
        Map<String, String> env = System.getenv();
        Path root = workspace();
        requireProfile(env);
        Path marker = createPhaseMarker(root, "started");
        Process initialized = E2aMysqlLauncher.start(env, root);
        if (initialized.isAlive()) throw new IllegalStateException("MySQL initialize did not finish");
        Files.writeString(marker, "initialized\n", java.nio.file.StandardOpenOption.APPEND);
        Process server = E2aMysqlLauncher.startServer(env, root);
        try {
            awaitPort();
            bootstrapAccount(env, root);
            JdbcTemplate jdbc = E2aMigrationEnvironment.connectLive(env, root);
            runMigration(jdbc, root);
            Files.writeString(marker, "completed\n", java.nio.file.StandardOpenOption.APPEND);
        } finally {
            E2aMysqlLauncher.shutdown(server, root);
            Files.writeString(marker, "stopped\n", java.nio.file.StandardOpenOption.APPEND);
            if (!E2aMysqlLauncher.isPortFree()) throw new IllegalStateException("19329 listener remains after shutdown");
        }
    }

    static Path workspace() {
        return Path.of("D:/acrylic-pda-worktrees/r06-e2a-migration");
    }

    static boolean portFree() {
        return E2aMysqlLauncher.isPortFree();
    }

    static void requireProfile(Map<String, String> env) {
        E2aMigrationEnvironment.requireExact(env, E2aMysqlLauncher.isPortFree(), workspace());
    }

    private static void awaitPort() throws InterruptedException {
        for (int i = 0; i < 60; i++) {
            if (!E2aMysqlLauncher.isPortFree()) return;
            Thread.sleep(500);
        }
        throw new IllegalStateException("MySQL 19329 did not become ready");
    }

    private static void runMigration(JdbcTemplate jdbc, Path root) throws Exception {
        Path marker = root.resolve(".local/e2a-target35.marker");
        if (Files.exists(marker)) throw new IllegalStateException("Existing E2a marker; refusing reset or reseed");
        Integer tables = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()", Integer.class);
        if (tables == null || tables != 0) throw new IllegalStateException("E2a schema is not empty; refusing reset");
        Flyway.configure().dataSource(jdbc.getDataSource()).target("34").load().migrate();
        E2aV34Seed.seed(jdbc, root.resolve(E2aMigrationEnvironment.STORAGE_DIR));
        var snapshot = new E2aMigrationSnapshot(jdbc, root, root.resolve(E2aMigrationEnvironment.STORAGE_DIR));
        JsonNode before = snapshot.capture(); snapshot.saveBefore("before-v35.json");
        Flyway.configure().dataSource(jdbc.getDataSource()).target("35").load().migrate();
        E2aV35NegativeChecks.verify(jdbc); snapshot.assertPreserved(before); snapshot.saveAfter("after-v35.json");
        Files.createFile(marker);
    }

    private static Path createPhaseMarker(Path root, String phase) throws Exception {
        Path marker = root.resolve(".local/e2a-launcher-" + phase + ".marker");
        Files.createDirectories(marker.getParent());
        Files.createFile(marker);
        Files.writeString(marker, phase + "\n", java.nio.file.StandardOpenOption.APPEND);
        return marker;
    }

    private static void bootstrapAccount(Map<String, String> env, Path root) throws Exception {
        String password = env.get("OUTBOUND_DB_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalStateException("Missing guarded DB password");
        Path sql = root.resolve(".local/evidence-e2a/bootstrap-account.sql");
        Files.createDirectories(sql.getParent());
        Files.writeString(sql, "CREATE DATABASE IF NOT EXISTS `" + E2aMigrationEnvironment.DATABASE + "`;\n"
            + "CREATE USER IF NOT EXISTS '" + E2aMigrationEnvironment.USER + "'@'127.0.0.1' IDENTIFIED BY '" + password.replace("'", "''") + "';\n"
            + "GRANT ALL ON `" + E2aMigrationEnvironment.DATABASE + "`.* TO '" + E2aMigrationEnvironment.USER + "'@'127.0.0.1';\nFLUSH PRIVILEGES;\n");
        Path defaults = root.resolve(".local/evidence-e2a/bootstrap.cnf");
        Files.writeString(defaults, "[client]\nuser=root\nhost=127.0.0.1\nport=" + E2aMigrationEnvironment.PORT + "\nprotocol=tcp\n");
        Process p = new ProcessBuilder("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe", "--defaults-extra-file=" + defaults,
            "--database=mysql").redirectInput(sql.toFile()).redirectErrorStream(true).start();
        if (!p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) || p.exitValue() != 0) throw new IllegalStateException("Account bootstrap failed");
    }
}
