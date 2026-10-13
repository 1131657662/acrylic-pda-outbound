package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.ExportNetHttpChecks;
import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aExportNetHttpIT {
    @Test void latestNetIsFixedAcrossCorrectionsRetriesAndRealDownloads() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        Flyway.configure().dataSource(jdbc.getDataSource()).load().migrate();
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var context = E2aCrossSourceHttpIT.start(profile, "--server.servlet.session.cookie.secure=false",
            "--outbound.corrections.commands.enabled=true",
            "--outbound.exports.renderer-home=" + profile.ROOT.resolve("report-rendering/build/install/acrylic-report-rendering"),
            "--outbound.exports.browsers=" + profile.ROOT.resolve("report-rendering/build/browsers"))) {
            ExportNetHttpChecks.verify((WebApplicationContext) context, profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "E05 real HTTP/MySQL latest selection, fixed jobs, source compatibility, downloads and rejection/rollback checks");
    }
}
