package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.correction.CorrectionAttachmentController;
import com.acrylic.outbound.inventory.CorrectionAttachmentChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aCorrectionAttachmentHttpIT {
    @Test void controlledBytesSurviveMigrationCreateRejectionsRollbackAndRestart() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "51"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile, "--server.servlet.session.cookie.secure=false",
                "--outbound.corrections.commands.enabled=true")) {
            assertThat(app.getBeansOfType(CorrectionAttachmentController.class)).isEmpty();
            CorrectionAttachmentChecks.seedLegacy((WebApplicationContext) app);
        }
        var sql = new CorrectionAttachmentSqlChecks(jdbc, profile.attempt); var tables = sql.tables();
        var before = sql.snapshot(tables, "attachment-migration-before");
        assertThat(before.get("correction_document")).isNotEmpty();
        assertThat(before.get("dispatch_record")).isNotEmpty();
        profile.migrate(jdbc, "52");
        assertThat(sql.snapshot(tables, "attachment-migration-after")).isEqualTo(before);
        verifyAndRestart(profile, sql);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Controlled bytes: nonempty migration preserved; actual Web HTTP size/hash/identity/CREATE/rollback/replay/restart; exact SQL guards");
    }

    private void verifyAndRestart(E2aCrossSourceProfile profile, CorrectionAttachmentSqlChecks sql) throws Exception {
        String[] options = {"--server.servlet.session.cookie.secure=false", "--outbound.corrections.commands.enabled=true",
            "--outbound.corrections.attachments.enabled=true"};
        try (var app = E2aCrossSourceHttpIT.start(profile, options)) {
            assertThat(app.getBeansOfType(CorrectionAttachmentController.class)).hasSize(1);
            new CorrectionAttachmentChecks((WebApplicationContext) app, profile.attempt).verify();
            sql.verifyGuards();
        }
        try (var app = E2aCrossSourceHttpIT.start(profile, options)) {
            CorrectionAttachmentChecks.restart(profile.attempt);
        }
    }
}
