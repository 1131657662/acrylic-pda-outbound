package com.acrylic.outbound.recovery;

import java.nio.file.*;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/** New main-tree profile; does not relax or reuse the historical e2a old-tree guard. */
final class C1MainProfile {
    static final Path ROOT = Path.of("D:/acrylic-pda-outbound");
    final Path attempt;
    final JdbcTemplate jdbc;

    C1MainProfile() throws Exception {
        String run = System.getenv("E2A_CROSS_RUN");
        assertThat(run).matches("[1-9][0-9]{2,3}"); assertThat(Integer.parseInt(run)).isGreaterThanOrEqualTo(557);
        assertThat(Path.of("").toRealPath()).isEqualTo(ROOT.resolve("server").toRealPath());
        attempt = ROOT.resolve(".local/e2a-cross-20260925-" + run);
        assertThat(attempt.toRealPath()).startsWith(ROOT.toRealPath());
        assertThat(attempt.resolve("test-started.marker")).doesNotExist();
        assertThat(System.getenv("E2A_CROSS_PASSWORD")).matches("[0-9a-f]{64}");
        for (String key : new String[]{"OUTBOUND_DB_RESET", "OUTBOUND_DB_REPAIR", "E2A_MIGRATION_RESET"})
            assertThat(System.getenv(key)).isNotEqualTo("true");
        String schema = "acrylic_e2a_cross_20260925_" + run, user = "e2a_cross_20260925_" + run;
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:19330/" + schema
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", user, System.getenv("E2A_CROSS_PASSWORD")));
        assertThat(jdbc.queryForObject("SELECT @@datadir", String.class).replace('\\', '/'))
            .isEqualToIgnoringCase(attempt.resolve("data").toString().replace('\\', '/') + "/");
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(schema);
        assertThat(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)).isEqualTo(user + "@127.0.0.1");
        assertThat(jdbc.queryForObject("SELECT @@port", Integer.class)).isEqualTo(19330);
        assertThat(jdbc.queryForObject("SELECT @@log_bin", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()", Integer.class)).isZero();
        Files.createFile(attempt.resolve("test-started.marker"));
    }

    void seedAndMigrate() throws Exception {
        Flyway.configure().dataSource(jdbc.getDataSource()).target("34").load().migrate();
        com.acrylic.outbound.e2averify.E2aV34Seed.seed(jdbc, attempt.resolve("storage"));
        Flyway.configure().dataSource(jdbc.getDataSource()).load().migrate();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission", Integer.class)).isPositive();
    }

    org.springframework.context.ConfigurableApplicationContext start(Class<?> extra, String... options) {
        var args = new java.util.ArrayList<>(java.util.List.of("--server.address=127.0.0.1", "--server.port=19437",
            "--spring.flyway.enabled=false", "--outbound.worker.enabled=false", "--outbound.bootstrap.enabled=false",
            // The admitted entry refuses to start while the framework session-expiry writer is enabled:
            // every start of this profile has to carry the disabled value (guard, S5).
            "--spring.session.jdbc.cleanup-cron=-"));
        args.addAll(java.util.List.of(options));
        return new org.springframework.boot.SpringApplication(com.acrylic.outbound.OutboundApplication.class, extra)
            .run(args.toArray(String[]::new));
    }
}
