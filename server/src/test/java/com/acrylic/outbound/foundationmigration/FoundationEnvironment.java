package com.acrylic.outbound.foundationmigration;

import java.nio.file.Path;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Guard executes before Spring or Flyway can initialize a datasource. */
public final class FoundationEnvironment {
    public static final String DATABASE = "acrylic_outbound_foundation_verify_20260921";
    public static final String USER = "foundation_verify_20260921";
    public static final Path ROOT = Path.of("D:/acrylic-pda-worktrees/r06-foundation-verification/.local");
    private FoundationEnvironment() { }

    static String suffix(Map<String, String> env) {
        String suffix = env.getOrDefault("FOUNDATION_CASE", "");
        if (!java.util.Set.of("", "dirty", "dirty2", "missing", "signature", "ddl", "lock").contains(suffix))
            throw new IllegalStateException("Unknown isolated case");
        return suffix.isEmpty() ? "" : "_" + suffix;
    }

    public static Path storage() { return ROOT.resolve("private-files" + suffix(System.getenv())); }
    public static String database() { return DATABASE + suffix(System.getenv()); }
    static String user(Map<String, String> env) { return suffix(env).isEmpty() ? USER : "fndv_20260921" + suffix(env); }

    public static void require() { validate(System.getenv()); }
    public static void requireSource() { requireSource(System.getenv()); }
    static void requireSource(Map<String, String> env) {
        validate(env);
        if (!suffix(env).isEmpty()) throw new IllegalStateException("Only the retained base namespace may create original evidence");
    }

    static void validate(Map<String, String> env) {
        String url = env.getOrDefault("OUTBOUND_DB_URL", "");
        String suffix = suffix(env);
        String expected = "jdbc:mysql://127.0.0.1:19324/" + DATABASE + suffix;
        if (!"true".equals(env.get("FOUNDATION_VERIFY")) || !user(env).equals(env.get("OUTBOUND_DB_USER"))
                || !url.equals(expected + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC")
                || !ROOT.resolve("private-files" + suffix).toString().equals(env.get("OUTBOUND_STORAGE")))
            throw new IllegalStateException("Foundation verification requires its exact isolated environment");
    }

    public static JdbcTemplate jdbc() {
        require();
        var source = new DriverManagerDataSource(System.getenv("OUTBOUND_DB_URL"), System.getenv("OUTBOUND_DB_USER"),
                System.getenv("OUTBOUND_DB_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        verifyConnected(jdbc);
        return jdbc;
    }

    public static void verifyConnected(JdbcTemplate jdbc) {
        String datadir = jdbc.queryForObject("SELECT @@datadir", String.class).replace('\\', '/');
        String expected = ROOT.resolve("mysql/data").toString().replace('\\', '/') + "/";
        if (!expected.equalsIgnoreCase(datadir)
                || !database().equals(jdbc.queryForObject("SELECT DATABASE()", String.class))
                || !Integer.valueOf(19324).equals(jdbc.queryForObject("SELECT @@port", Integer.class))
                || !jdbc.queryForObject("SELECT CURRENT_USER()", String.class).startsWith(user(System.getenv()) + "@"))
            throw new IllegalStateException("Connected foundation database does not match guarded identity");
    }

    public static Path evidence(String name) {
        if (!name.matches("[a-z0-9-]+\\.json")) throw new IllegalArgumentException("Unexpected evidence name");
        return ROOT.resolve("evidence").resolve(name);
    }
}
