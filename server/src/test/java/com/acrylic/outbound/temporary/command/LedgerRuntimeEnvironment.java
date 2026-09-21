package com.acrylic.outbound.temporary.command;

import java.nio.file.Path;
import org.springframework.context.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Exact author environment guard, called before Spring can run Flyway. Never used in production. */
public class LedgerRuntimeEnvironment implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public static final Path ROOT=Path.of("D:/acrylic-pda-worktrees/r06-ledger-runtime/.local");
    public static final String URL="jdbc:mysql://127.0.0.1:19326/acrylic_outbound_ledger_20260921?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&rewriteBatchedStatements=true";
    @Override public void initialize(ConfigurableApplicationContext context) {
        var env=System.getenv();String user="outbound_ledger_20260921";
        if(!"true".equals(env.get("LEDGER_RUNTIME_VERIFY")) || !URL.equals(env.get("OUTBOUND_DB_URL"))
            || !user.equals(env.get("OUTBOUND_DB_USER")) || !ROOT.resolve("private-files").toString().equals(env.get("OUTBOUND_STORAGE"))
            || !"true".equals(env.get("OUTBOUND_MIGRATE")))throw new IllegalStateException("Exact ledger author environment required");
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(URL,user,env.get("OUTBOUND_DB_PASSWORD")));
        String datadir=jdbc.queryForObject("SELECT @@datadir",String.class).replace('\\','/');
        if(!datadir.equalsIgnoreCase(ROOT.resolve("mysql19326/data").toString().replace('\\','/')+"/")
            || !Integer.valueOf(19326).equals(jdbc.queryForObject("SELECT @@port",Integer.class))
            || !"acrylic_outbound_ledger_20260921".equals(jdbc.queryForObject("SELECT DATABASE()",String.class))
            || !jdbc.queryForObject("SELECT CURRENT_USER()",String.class).startsWith(user+"@"))throw new IllegalStateException("Unexpected connected ledger instance");
    }
}
