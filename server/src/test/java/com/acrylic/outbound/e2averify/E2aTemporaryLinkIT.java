package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryLinkChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Isolated nonempty MySQL verification of the M2b-2b controlled-linkage manual confirmation gate. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aTemporaryLinkIT {
    @Test void controlledLinkageRequiresManualConfirmationOnNonemptyMysql() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class)).isPositive();
        profile.migrate(jdbc,"41");E2aCrossSourceHttpSetup.signing(profile.attempt);
        try(var context=E2aCrossSourceHttpIT.start(profile)) {
            new TemporaryLinkChecks((WebApplicationContext)context).verify();
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Controlled linkage manual confirmation and pointer switch; production operations remain absent\n");
    }
}
