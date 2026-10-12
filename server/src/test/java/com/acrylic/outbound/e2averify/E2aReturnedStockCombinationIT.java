package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.*;
import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aReturnedStockCombinationIT {
    @Test void returnedDiscoveryAccountingAndMissingPostingShareTheSameLockedKernel() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34"); E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        Flyway.configure().dataSource(jdbc.getDataSource()).load().migrate();
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        String renderer = profile.ROOT.resolve("report-rendering/build/install/acrylic-report-rendering").toString();
        String browsers = profile.ROOT.resolve("report-rendering/build/browsers").toString();
        String[] options = {"--server.servlet.session.cookie.secure=false",
            "--outbound.corrections.commands.enabled=true","--outbound.corrections.attachments.enabled=true",
            "--outbound.exports.renderer-home="+renderer,"--outbound.exports.browsers="+browsers};
        // Independent business scenarios keep their normal login windows; do not weaken the production throttle.
        try (var context = E2aCrossSourceHttpIT.start(profile,options)) {
            CorrectionAccountingHttpChecks.verify((WebApplicationContext)context,profile.attempt);
        }
        try (var context = E2aCrossSourceHttpIT.start(profile,options)) {
            ReconciliationMissingPostingChecks.verify((WebApplicationContext)context,profile.attempt);
        }
        try (var context = E2aCrossSourceHttpIT.start(profile,options)) {
            ReturnedStockPagingChecks.verify((WebApplicationContext)context,profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Actual HTTP bounded paging and untrusted candidate rejection; affected accounting, withdrawal and missing-posting combination");
    }
}
