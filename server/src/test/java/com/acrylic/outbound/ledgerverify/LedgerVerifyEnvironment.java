package com.acrylic.outbound.ledgerverify;

import java.nio.file.Path;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Exact dedicated-lab guard, evaluated before Spring/Flyway can write. */
public final class LedgerVerifyEnvironment {
    public static final String DATABASE = "acrylic_outbound_ledger_verify_20260921_r2";
    public static final String USER = "ledger_verify_r2_20260921";
    public static final Path ROOT = Path.of("D:/acrylic-pda-worktrees/r06-ledger-verification/.local");
    static final String URL = "jdbc:mysql://127.0.0.1:19327/" + DATABASE
        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private LedgerVerifyEnvironment() { }

    public static void require() { validate(System.getenv()); }
    static void validate(Map<String,String> env) {
        if (!"true".equals(env.get("LEDGER_VERIFY")) || !URL.equals(env.get("OUTBOUND_DB_URL"))
            || !USER.equals(env.get("OUTBOUND_DB_USER"))
            || !ROOT.resolve("private-files-r2").toString().equals(env.get("OUTBOUND_STORAGE"))
            || !ROOT.resolve("signing-private-r2.der").toString().equals(env.get("OUTBOUND_AUTH_SIGNING_PRIVATE_KEY"))
            || !ROOT.resolve("signing-public-r2.der").toString().equals(env.get("OUTBOUND_AUTH_SIGNING_PUBLIC_KEY")))
            throw new IllegalStateException("Ledger verification requires its exact isolated environment");
    }

    public static JdbcTemplate jdbc() {
        require();
        var source = new DriverManagerDataSource(System.getenv("OUTBOUND_DB_URL"), USER, System.getenv("OUTBOUND_DB_PASSWORD"));
        var jdbc = new JdbcTemplate(source); verifyConnected(jdbc); return jdbc;
    }

    public static void verifyConnected(JdbcTemplate jdbc) {
        String datadir = jdbc.queryForObject("SELECT @@datadir", String.class).replace('\\','/');
        String expected = ROOT.resolve("mysql/data").toString().replace('\\','/') + "/";
        if (!expected.equalsIgnoreCase(datadir) || !DATABASE.equals(jdbc.queryForObject("SELECT DATABASE()",String.class))
            || !Integer.valueOf(19327).equals(jdbc.queryForObject("SELECT @@port",Integer.class))
            || !(USER + "@127.0.0.1").equals(jdbc.queryForObject("SELECT CURRENT_USER()",String.class)))
            throw new IllegalStateException("Connected ledger database does not match guarded identity");
    }

    public static Path evidence(String name) {
        if (!name.matches("[a-z0-9-]+\\.json")) throw new IllegalArgumentException("Unexpected evidence name");
        return ROOT.resolve("evidence-r2").resolve(name);
    }
}
