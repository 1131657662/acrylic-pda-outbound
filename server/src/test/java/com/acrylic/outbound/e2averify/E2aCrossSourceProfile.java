package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** One-shot local test profile. It cannot target a shared or previously seeded schema. */
final class E2aCrossSourceProfile {
    /**
     * The tree the harness provisions and verifies. It defaults to the shared migration worktree;
     * {@code E2A_CROSS_ROOT} points it at the tree under test, and every other check (attempt under
     * that root, datadir identity, one-shot marker, port, credential) still applies.
     */
    static final Path ROOT = Path.of(System.getenv().getOrDefault("E2A_CROSS_ROOT",
        "D:/acrylic-pda-worktrees/r06-e2a-migration"));
    final Path attempt;
    final String database, user, url;

    E2aCrossSourceProfile() throws Exception {
        String run = System.getenv("E2A_CROSS_RUN");
        if (run == null || !run.matches("[1-9][0-9]{0,2}")) throw new IllegalStateException("Invalid run allocation");
        attempt = ROOT.resolve(".local/e2a-cross-20260925-" + run);
        database = "acrylic_e2a_cross_20260925_" + run;
        user = "e2a_cross_20260925_" + run;
        url = "jdbc:mysql://127.0.0.1:19330/" + database
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        require(ROOT.resolve("server").toRealPath().equals(Path.of("").toRealPath()), "Workspace mismatch");
        require(attempt.toRealPath().startsWith(ROOT.toRealPath()), "Attempt escapes workspace");
        require(Files.isDirectory(attempt.resolve("data")), "Missing isolated datadir");
        require(!Files.exists(attempt.resolve("test-started.marker")), "One-shot attempt already used");
        require(System.getenv("E2A_CROSS_PASSWORD") != null
            && System.getenv("E2A_CROSS_PASSWORD").matches("[0-9a-f]{64}"), "Missing private credential");
        for (String key : new String[]{"OUTBOUND_DB_RESET", "OUTBOUND_DB_REPAIR", "E2A_MIGRATION_RESET"})
            require(!"true".equals(System.getenv(key)), "Reset/repair forbidden");
    }

    JdbcTemplate connect() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, System.getenv("E2A_CROSS_PASSWORD")));
        String actual = jdbc.queryForObject("SELECT @@datadir", String.class).replace('\\', '/');
        String expected = attempt.resolve("data").toString().replace('\\', '/') + "/";
        require(expected.equalsIgnoreCase(actual), "Datadir mismatch");
        require(database.equals(jdbc.queryForObject("SELECT DATABASE()", String.class)), "Schema mismatch");
        require((user + "@127.0.0.1").equals(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)), "User mismatch");
        require(jdbc.queryForObject("SELECT @@port", Integer.class) == 19330, "Port mismatch");
        require(jdbc.queryForObject("SELECT @@log_bin", Integer.class) == 1, "Binlog must remain enabled");
        require(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()",
            Integer.class) == 0, "Nonempty schema cannot be reseeded");
        return jdbc;
    }

    void migrate(JdbcTemplate jdbc, String target) {
        Flyway.configure().dataSource(jdbc.getDataSource()).target(target).load().migrate();
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
