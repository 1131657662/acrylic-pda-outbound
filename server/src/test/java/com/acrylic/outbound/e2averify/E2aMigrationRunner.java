package com.acrylic.outbound.e2averify;

import java.nio.file.Path;
import java.util.Map;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
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
        Process initialized = E2aMysqlLauncher.start(env, root);
        if (initialized.isAlive()) throw new IllegalStateException("MySQL initialize did not finish");
        Process server = E2aMysqlLauncher.startServer(env, root);
        try {
            awaitPort();
            JdbcTemplate jdbc = E2aMigrationEnvironment.connect(env, false, root);
            runMigration(jdbc, root);
        } finally {
            E2aMysqlLauncher.shutdown(server, root);
            if (portFree() == false) throw new IllegalStateException("19329 listener remains after shutdown");
        }
    }

    static Path workspace() {
        return Path.of("D:/acrylic-pda-worktrees/r06-e2a-migration");
    }

    static boolean portFree() {
        String value = System.getenv().getOrDefault("E2A_MIGRATION_PORT_FREE", "false");
        return "true".equals(value);
    }

    static void requireProfile(Map<String, String> env) {
        E2aMigrationEnvironment.requireExact(env, "true".equals(env.get("E2A_MIGRATION_PORT_FREE")), workspace());
    }

    private static void awaitPort() throws InterruptedException {
        for (int i = 0; i < 60; i++) {
            if (!portFree()) return;
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
}
