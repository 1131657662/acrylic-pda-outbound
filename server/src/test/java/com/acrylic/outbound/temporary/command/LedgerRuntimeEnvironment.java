package com.acrylic.outbound.temporary.command;

import java.nio.file.Path;
import java.util.*;
import org.springframework.context.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Exact author environment guard, called before Spring can run Flyway. Never used in production. */
public class LedgerRuntimeEnvironment implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public static final Path ROOT=Path.of("D:/acrylic-pda-worktrees/r06-ledger-runtime/.local");
    public static final String URL="jdbc:mysql://127.0.0.1:19326/acrylic_outbound_ledger_20260921?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&rewriteBatchedStatements=true";
    private static final Profile AUTHOR=new Profile(ROOT,19326,"acrylic_outbound_ledger_20260921","outbound_ledger_20260921","private.pk8");
    private static final Profile COMBINATION=new Profile(Path.of("D:/acrylic-pda-worktrees/sixth-wave-integration/.local"),
        19328,"acrylic_outbound_wave6_20260921","outbound_wave6_20260921","private.der");
    public static Path root() { return validate(System.getenv()).root(); }
    @Override public void initialize(ConfigurableApplicationContext context) {
        var env=System.getenv();var profile=validate(env);
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(profile.url(),profile.user(),env.get("OUTBOUND_DB_PASSWORD")));
        connected(profile,jdbc.queryForObject("SELECT @@datadir",String.class),jdbc.queryForObject("SELECT @@port",Integer.class),
            jdbc.queryForObject("SELECT DATABASE()",String.class),jdbc.queryForObject("SELECT CURRENT_USER()",String.class));
    }
    static Profile validate(Map<String,String> env) {
        if(!"true".equals(env.get("LEDGER_RUNTIME_VERIFY")) || !"true".equals(env.get("OUTBOUND_MIGRATE")))
            throw new IllegalStateException("Explicit ledger test/migration enable required");
        return List.of(AUTHOR,COMBINATION).stream().filter(profile->profile.matches(env)).findFirst()
            .orElseThrow(()->new IllegalStateException("Exact assigned ledger environment required"));
    }
    static void connected(Profile profile,String datadir,int port,String database,String account) {
        if(!datadir.replace('\\','/').equalsIgnoreCase(profile.datadir().toString().replace('\\','/')+"/")
            || port!=profile.port() || !profile.database().equals(database) || !(profile.user()+"@127.0.0.1").equals(account))
            throw new IllegalStateException("Unexpected connected ledger instance");
    }
    record Profile(Path root,int port,String database,String user,String privateFile) {
        String url() { return "jdbc:mysql://127.0.0.1:"+port+"/"+database+"?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&rewriteBatchedStatements=true"; }
        Path datadir() { return root.resolve("mysql"+port+"/data"); }
        boolean matches(Map<String,String> env) {
            return url().equals(env.get("OUTBOUND_DB_URL")) && user.equals(env.get("OUTBOUND_DB_USER"))
                && same(root.resolve("private-files"),env.get("OUTBOUND_STORAGE"))
                && same(root.resolve("signing/"+privateFile),env.get("OUTBOUND_AUTH_SIGNING_PRIVATE_KEY"))
                && same(root.resolve("signing/public.der"),env.get("OUTBOUND_AUTH_SIGNING_PUBLIC_KEY"));
        }
        private boolean same(Path expected,String actual) { return actual!=null && expected.equals(Path.of(actual)); }
    }
}
