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
        requireTarget35Resource(root);
        Path marker = createPhaseMarker(root, "started");
        Process initialized = E2aMysqlLauncher.start(env, root);
        if (initialized.isAlive()) throw new IllegalStateException("MySQL initialize did not finish");
        Files.writeString(marker, "initialized\n", java.nio.file.StandardOpenOption.APPEND);
        Process server = E2aMysqlLauncher.startServer(env, root);
        Path data = root.resolve(E2aMigrationEnvironment.DATA_DIR).toAbsolutePath().normalize();
        try {
            E2aMysqlLauncher.awaitReady(server);
            E2aMysqlLauncher.verifyProcessDataDir(server, data);
            requireOwnedListener(server);
            bootstrapAccount(env, root);
            JdbcTemplate jdbc = E2aMigrationEnvironment.connectLive(env, root);
            runMigration(jdbc, root);
            Files.writeString(marker, "completed\n", java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception failure) {
            Files.writeString(marker, "failed: " + failure.getClass().getSimpleName() + "\n", java.nio.file.StandardOpenOption.APPEND);
            throw failure;
        } finally {
            if (server.isAlive()) requireOwnedListener(server);
            E2aMysqlLauncher.shutdown(server, root, "root", "");
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

    private static void requireOwnedListener(Process server) throws Exception {
        if (!server.isAlive()) throw new IllegalStateException("MySQL server exited before listener verification");
        String command = "(Get-NetTCPConnection -LocalAddress '127.0.0.1' -LocalPort "
            + E2aMigrationEnvironment.PORT + " -State Listen -ErrorAction SilentlyContinue).OwningProcess";
        Process probe = new ProcessBuilder("powershell", "-NoProfile", "-Command", command)
            .redirectErrorStream(true).start();
        if (!probe.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Listener ownership probe timed out");
        String output = new String(probe.getInputStream().readAllBytes()).trim();
        if (!Long.toString(server.pid()).equals(output)) throw new IllegalStateException("19329 listener is not owned by this MySQL process");
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

    private static void requireTarget35Resource(Path root) throws Exception {
        Path migrationRoot = root.resolve("server/src/main/resources/db/migration").toAbsolutePath().normalize();
        try (var files = Files.list(migrationRoot)) {
            boolean present = files.anyMatch(E2aMigrationRunner::isNonEmptyV35);
            if (!present) throw new IllegalStateException("Required V35 migration resource is missing");
        }
    }

    private static boolean isNonEmptyV35(Path path) {
        try {
            return path.getFileName().toString().matches("V35__.+\\.sql")
                && Files.isRegularFile(path) && Files.size(path) > 0;
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot inspect V35 migration resource", error);
        }
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
        Path sql = E2aMysqlLauncher.privateArtifact(root, "bootstrap-account.sql");
        Files.writeString(sql, "CREATE DATABASE IF NOT EXISTS `" + E2aMigrationEnvironment.DATABASE + "`;\n"
            + "CREATE USER IF NOT EXISTS '" + E2aMigrationEnvironment.USER + "'@'127.0.0.1' IDENTIFIED BY '" + password.replace("'", "''") + "';\n"
            + "GRANT ALL ON `" + E2aMigrationEnvironment.DATABASE + "`.* TO '" + E2aMigrationEnvironment.USER + "'@'127.0.0.1';\n"
            + "GRANT SHUTDOWN ON *.* TO '" + E2aMigrationEnvironment.USER + "'@'127.0.0.1';\nFLUSH PRIVILEGES;\n",
            java.nio.file.StandardOpenOption.CREATE_NEW);
        Path defaults = E2aMysqlLauncher.privateArtifact(root, "bootstrap.cnf");
        Files.writeString(defaults, "[client]\nuser=root\nhost=127.0.0.1\nport=" + E2aMigrationEnvironment.PORT + "\nprotocol=tcp\n",
            java.nio.file.StandardOpenOption.CREATE_NEW);
        Path log = E2aMysqlLauncher.privateArtifact(root, "mysql-bootstrap-" + java.util.UUID.randomUUID() + ".log");
        Process p = new ProcessBuilder("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe", "--defaults-file=" + defaults,
            "--database=mysql").redirectInput(sql.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("Account bootstrap timed out");
        }
        if (p.exitValue() != 0) throw new IllegalStateException("Account bootstrap failed: " + p.exitValue());
    }
}
