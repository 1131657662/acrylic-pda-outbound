package com.acrylic.outbound.e2averify;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Test-only guard values; callers must invoke before any database or process action. */
public final class E2aMigrationEnvironment {
    private static final Path EXPECTED_WORKSPACE = Path.of("D:/acrylic-pda-worktrees/r06-e2a-migration");
    public static final String HOST = "127.0.0.1";
    public static final int PORT = 19329;
    public static final String DATABASE = "acrylic_outbound_e2a_migrate_20260921";
    public static final String USER = "e2a_migrate_20260921";
    public static final Path DATA_DIR = Path.of(".local/mysql19329/data");
    public static final Path STORAGE_DIR = Path.of(".local/private-files");
    public static final Path SIGNING_DIR = Path.of(".local/signing");
    public static final String URL = "jdbc:mysql://127.0.0.1:" + PORT + "/" + DATABASE
        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    public static final Path EVIDENCE_DIR = Path.of(".local/evidence-e2a");

    private E2aMigrationEnvironment() { }

    /** Full preflight validation; callers must invoke before SQL, Flyway, or process startup. */
    public static void requireExact(Map<String, String> env, boolean portFree, Path workspace) {
        Path actualWorkspace = workspace.toAbsolutePath().normalize();
        Path expectedWorkspace = EXPECTED_WORKSPACE.toAbsolutePath().normalize();
        if (!portFree || !actualWorkspace.equals(expectedWorkspace)) throw new IllegalStateException("E2a migration environment guard failed: port/workspace");
        requireProfile(env, workspace);
    }

    public static void requireProfile(Map<String, String> env, Path workspace) {
        Path actualWorkspace = workspace.toAbsolutePath().normalize();
        if (!actualWorkspace.equals(EXPECTED_WORKSPACE.toAbsolutePath().normalize())) throw new IllegalStateException("Unexpected E2a workspace");
        requireUnderWorkspace(actualWorkspace, DATA_DIR, "datadir");
        requireUnderWorkspace(actualWorkspace, STORAGE_DIR, "storage");
        requireUnderWorkspace(actualWorkspace, SIGNING_DIR, "signing");
        if (!"true".equals(env.get("E2A_MIGRATION_VERIFY"))
                || !"true".equals(env.get("OUTBOUND_MIGRATE"))
                || !URL.equals(env.get("OUTBOUND_DB_URL"))
                || !USER.equals(env.get("OUTBOUND_DB_USER"))) {
            throw new IllegalStateException("E2a migration requires its exact URL/schema/account");
        }
        for (String forbidden : new String[]{"E2A_MIGRATION_RESET", "E2A_MIGRATION_REPAIR",
                "OUTBOUND_DB_RESET", "OUTBOUND_DB_REPAIR", "E2A_REUSE_SEED"}) {
            if ("true".equals(env.get(forbidden))) {
                throw new IllegalStateException("E2a migration reset/repair is forbidden: " + forbidden);
            }
        }
        requirePath(env, "OUTBOUND_DATADIR", DATA_DIR, workspace);
        requirePath(env, "OUTBOUND_STORAGE", STORAGE_DIR, workspace);
        requirePath(env, "OUTBOUND_AUTH_SIGNING_PRIVATE_KEY", SIGNING_DIR.resolve("private.der"), workspace);
        requirePath(env, "OUTBOUND_AUTH_SIGNING_PUBLIC_KEY", SIGNING_DIR.resolve("public.der"), workspace);
    }

    public static JdbcTemplate connect(Map<String, String> env, boolean portFree, Path workspace) {
        requireExact(env, portFree, workspace);
        var source = new DriverManagerDataSource(URL, USER, env.get("OUTBOUND_DB_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        verifyConnected(jdbc, workspace);
        return jdbc;
    }

    public static JdbcTemplate connectLive(Map<String, String> env, Path workspace) {
        if (E2aMysqlLauncher.isPortFree()) throw new IllegalStateException("E2a live port is not listening");
        requireProfile(env, workspace);
        var source = new DriverManagerDataSource(URL, USER, env.get("OUTBOUND_DB_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        verifyConnected(jdbc, workspace);
        return jdbc;
    }

    public static void verifyConnected(JdbcTemplate jdbc, Path workspace) {
        String datadir = jdbc.queryForObject("SELECT @@datadir", String.class).replace('\\', '/');
        String expected = workspace.resolve(DATA_DIR).toAbsolutePath().normalize().toString().replace('\\', '/') + "/";
        if (!expected.equalsIgnoreCase(datadir)
                || !DATABASE.equals(jdbc.queryForObject("SELECT DATABASE()", String.class))
                || PORT != jdbc.queryForObject("SELECT @@port", Integer.class)
                || !(USER + "@127.0.0.1").equals(jdbc.queryForObject("SELECT CURRENT_USER()", String.class))) {
            throw new IllegalStateException("Unexpected connected E2a migration instance");
        }
    }

    public static Path evidence(Path workspace, String name) {
        if (!name.matches("[a-z0-9-]+\\.json")) throw new IllegalArgumentException("Unexpected evidence name");
        Path root = workspace.resolve(EVIDENCE_DIR).normalize();
        requireUnderWorkspace(workspace.toAbsolutePath().normalize(), EVIDENCE_DIR, "evidence");
        return root.resolve(name);
    }

    private static void requireUnderWorkspace(Path workspace, Path relative, String label) {
        Path resolved = workspace.resolve(relative).normalize();
        if (!resolved.startsWith(workspace) || escapesThroughLink(workspace, resolved)) {
            throw new IllegalStateException("E2a migration environment guard failed: " + label);
        }
    }

    private static void requirePath(Map<String, String> env, String name, Path relative, Path workspace) {
        String configured = env.get(name);
        if (configured == null || configured.contains("..")) {
            throw new IllegalStateException("E2a migration path is missing or non-canonical: " + name);
        }
        Path expected = workspace.resolve(relative).normalize();
        Path actual = Path.of(configured).toAbsolutePath().normalize();
        if (!actual.equals(expected) || escapesThroughLink(workspace, actual)) {
            throw new IllegalStateException("E2a migration path mismatch: " + name);
        }
    }

    private static boolean escapesThroughLink(Path workspace, Path candidate) {
        try {
            Path root = workspace.toAbsolutePath().normalize().toRealPath();
            Path current = root;
            Path relative = workspace.toAbsolutePath().normalize().relativize(candidate);
            for (Path part : relative) {
                current = current.resolve(part);
                if (Files.exists(current) && !current.toRealPath().startsWith(root)) return true;
            }
            return false;
        } catch (java.io.IOException error) {
            throw new IllegalStateException("E2a migration path cannot be canonicalized", error);
        }
    }
}
