package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/** Owned synthetic closed registration in one new real schema; not a complete recovery supplier. */
final class SpringClosedAdmissionFixture {
    private static final com.fasterxml.jackson.databind.ObjectMapper TABLE_JSON = RecoveryControlFixture.JSON.copy()
        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    final JdbcTemplate raw;
    final RecoveryControlFixture control;
    final Path output;

    SpringClosedAdmissionFixture() throws Exception {
        EffectRealEnvironment.requireEnvironment();
        output = Path.of(System.getenv("B11_EFFECT_EVIDENCE_DIR"), "private");
        Files.createDirectories(output);
        var source = new DriverManagerDataSource(EffectRealEnvironment.URL, EffectRealEnvironment.USER,
            System.getenv("OUTBOUND_DB_PASSWORD"));
        Flyway.configure().dataSource(source).load().migrate();
        raw = new JdbcTemplate(source);
        control = ClosedAdmissionInstanceModel.create(raw, output);
        ClosedBorrowModelProtocol.install(control);
    }

    ConfigurableApplicationContext start(String label) {
        var application = new SpringApplication(com.acrylic.outbound.OutboundApplication.class,
            SpringClosedAdmissionProbe.class);
        application.setLogStartupInfo(false);
        var options = new ArrayList<>(List.of("--server.address=127.0.0.1", "--server.port=0",
            "--spring.flyway.enabled=false", "--spring.session.jdbc.cleanup-cron=-",
            "--server.servlet.session.cookie.secure=false", "--outbound.worker.enabled=false",
            "--outbound.bootstrap.enabled=false", "--outbound.storage=" + output.resolve("storage-" + label),
            "--outbound.recovery.admission.enabled=true", "--outbound.recovery.baseline=1",
            "--outbound.recovery.root=" + control.root, "--outbound.recovery.installation-id=" + control.installation,
            "--outbound.recovery.restore-roots=" + control.restoreRoot,
            "--outbound.recovery.checkpoint=" + control.checkpoint));
        return application.run(options.toArray(String[]::new));
    }

    void pendingRecovery() {
        assertThat(raw.update("UPDATE server_state SET service_mode='RECOVERY_PENDING' WHERE id=1")).isEqualTo(1);
    }

    void restoreOwnedMode() {
        assertThat(raw.update("UPDATE server_state SET service_mode='NORMAL' WHERE id=1")).isEqualTo(1);
    }

    Map<String, String> tables() {
        var result = new TreeMap<String, String>();
        var names = raw.queryForList("SELECT TABLE_NAME FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY TABLE_NAME",
            String.class);
        for (String table : names) {
            assertThat(table).matches("[A-Za-z0-9_]+");
            var rows = raw.queryForList("SELECT * FROM `" + table + "`");
            var serialized = rows.stream().map(row -> {
                try { return TABLE_JSON.writeValueAsString(new TreeMap<>(row)); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
            }).sorted().toList();
            result.put(table, RecoveryControlFormat.sha256(String.join("\n", serialized).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        return result;
    }

    void save(String name, Object value) throws Exception {
        Files.writeString(output.resolve(name), RecoveryControlFixture.JSON.writeValueAsString(value), StandardOpenOption.CREATE_NEW);
    }
}
