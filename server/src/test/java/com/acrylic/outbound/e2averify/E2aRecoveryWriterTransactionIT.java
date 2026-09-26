package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryExportChecks;
import com.acrylic.outbound.recovery.RecoveryTransactionChecks;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aRecoveryWriterTransactionIT {
    @Test void holdsWriterLeaseThroughRealTransactionsAndPreservesExistingHttpExports() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker")); profile.migrate(jdbc,"34");
        E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        int submissions = jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class);
        assertThat(submissions).isPositive(); profile.migrate(jdbc,"38");
        RecoveryTransactionChecks.verify(jdbc, profile.attempt);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class)).isEqualTo(submissions);
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        Path renderer=Path.of("../report-rendering/build/install/acrylic-report-rendering").toAbsolutePath().normalize();
        try(var context=E2aCrossSourceHttpIT.start(profile,"--outbound.exports.sample-data=true","--server.servlet.session.cookie.secure=false",
                "--outbound.exports.renderer-home="+renderer,
                "--outbound.exports.browsers=D:/acrylic-pda-worktrees/r08-export/report-rendering/build/browsers")) {
            new TemporaryExportChecks((WebApplicationContext)context).verify(profile.attempt.resolve("exports-verified"));
            E2aTemporaryExportBrowser.verify(profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Real transaction lease checks; separate existing HTTP export regression; API shared gate not integrated\n");
    }
}
