package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.correction.CorrectionReadController;
import com.acrylic.outbound.inventory.CorrectionReadChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aCorrectionReadHttpIT {
    @Test void readsContextsAndOriginalReceiptsWithoutWritingBusinessFacts() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "52"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile, "--server.servlet.session.cookie.secure=false")) {
            assertThat(app.getBeansOfType(CorrectionReadController.class)).isEmpty();
        }
        String[] options = {"--server.servlet.session.cookie.secure=false", "--outbound.corrections.commands.enabled=true"};
        try (var app = E2aCrossSourceHttpIT.start(profile, options)) {
            assertThat(app.getBeansOfType(CorrectionReadController.class)).hasSize(1);
            new CorrectionReadChecks((WebApplicationContext) app, profile.attempt).verify();
        }
        try (var app = E2aCrossSourceHttpIT.start(profile, options)) {
            CorrectionReadChecks.restart((WebApplicationContext) app, profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Real HTTP GET context -> CREATE -> original document -> APPROVE -> GET original responses; restart; 15 tables read invariance");
    }
}
