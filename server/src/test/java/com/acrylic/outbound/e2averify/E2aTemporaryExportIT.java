package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryExportChecks;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aTemporaryExportIT {
    @Test void exportsOriginalTemporaryConfirmationsThroughRealHttp() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));profile.migrate(jdbc,"34");
        E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class)).isPositive();
        profile.migrate(jdbc,"38");E2aCrossSourceHttpSetup.signing(profile.attempt);
        Path renderer=Path.of("../report-rendering/build/install/acrylic-report-rendering").toAbsolutePath().normalize();
        try(var context=E2aCrossSourceHttpIT.start(profile,"--outbound.exports.sample-data=true","--server.servlet.session.cookie.secure=false",
                "--outbound.exports.renderer-home="+renderer,
                "--outbound.exports.browsers=D:/acrylic-pda-worktrees/r08-export/report-rendering/build/browsers")) {
            new TemporaryExportChecks((WebApplicationContext)context).verify(profile.attempt.resolve("exports-verified"));
            E2aTemporaryExportBrowser.verify(profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"TEMP normal/short original exports and failure closure; FORMAL/PC compatibility\n");
    }
}
