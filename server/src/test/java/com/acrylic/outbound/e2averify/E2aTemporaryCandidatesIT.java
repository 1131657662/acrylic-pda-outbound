package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryCandidateChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aTemporaryCandidatesIT {
    @Test void formalCommandsExposeReadOnlyTemporaryHintsOverRealHttp() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class)).isPositive();
        profile.migrate(jdbc,"36");E2aCrossSourceHttpSetup.signing(profile.attempt);
        try(var context=E2aCrossSourceHttpIT.start(profile,"--server.servlet.session.cookie.secure=false")) {
            new TemporaryCandidateChecks((WebApplicationContext)context).verify(profile.attempt);
            E2aTemporaryCandidatesBrowser.verify(profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Internal TEMP success; authenticated real formal HTTP candidates, unchanged original evidence\n");
    }
}
