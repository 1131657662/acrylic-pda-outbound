package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.OutboundApplication;
import com.acrylic.outbound.inventory.CorrectionAccountingHttpChecks;
import com.acrylic.outbound.inventory.ExportNetCombinedChecks;
import java.nio.file.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "E05_HISTORY_VERIFY", matches = "true")
class E2aExportNetCombinedIT {
    @Test void exportHonorsNewWorkSourceRetainedCapacityAndCommittedRevocation() throws Exception {
        String run = System.getenv("E05_HISTORY_RUN"); assertThat(run).matches("[1-9][0-9]{3}");
        Path root = Path.of(System.getenv("E2A_CROSS_ROOT")).toRealPath();
        assertThat(root.resolve("server")).isEqualTo(Path.of("").toRealPath());
        Path attempt = root.resolve(".local/e2a-cross-20260925-" + run);
        assertThat(attempt.resolve("export-history-source.json")).isRegularFile();
        Files.createFile(attempt.resolve("test-started.marker"));
        String database = "acrylic_e2a_cross_20260925_" + run;
        var source = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:19331/" + database
            + "?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true", "e2a_cross_20260925_" + run, System.getenv("E2A_CROSS_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(database);
        assertThat(Path.of(jdbc.queryForObject("SELECT @@datadir", String.class)).toRealPath()).isEqualTo(attempt.resolve("data").toRealPath());
        Flyway.configure().dataSource(source).load().migrate();
        try (var context = SpringApplication.run(OutboundApplication.class, options(attempt, source))) {
            ExportNetCombinedChecks.verify((WebApplicationContext) context, attempt);
            CorrectionAccountingHttpChecks.verify((WebApplicationContext) context, attempt);
        }
        Files.writeString(attempt.resolve("test-completed.marker"), "Real HTTP recalled source, capacity, revocation and correction accounting combination passed");
    }

    private static String[] options(Path attempt, DriverManagerDataSource source) {
        return new String[]{"--server.address=127.0.0.1", "--server.port=19434", "--spring.flyway.enabled=false",
            "--spring.datasource.url=" + source.getUrl(), "--spring.datasource.username=" + source.getUsername(),
            "--spring.datasource.password=" + source.getPassword(), "--outbound.storage=" + attempt.resolve("storage"),
            "--outbound.signing.private-key=" + attempt.resolve("private/server-private.der"),
            "--outbound.signing.public-key=" + attempt.resolve("private/server-public.der"),
            "--outbound.worker.enabled=false", "--outbound.bootstrap.enabled=false", "--spring.session.jdbc.cleanup-cron=-",
            "--server.servlet.session.cookie.secure=false", "--outbound.corrections.commands.enabled=true", "--outbound.recall.fault-withdrawal-enabled=true",
            "--outbound.exports.renderer-home=" + E2aWorktree.ROOT.resolve("report-rendering/build/install/acrylic-report-rendering"),
            "--outbound.exports.browsers=" + E2aWorktree.ROOT.resolve("report-rendering/build/browsers")};
    }
}
