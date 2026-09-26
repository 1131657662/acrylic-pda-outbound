package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryConfirmationChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aTemporaryConfirmationIT {
    @Test void internalDecisionsAndAccountingKeepOriginalReceiptsOnNonemptyMysql() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class)).isPositive();
        profile.migrate(jdbc,"36");E2aCrossSourceHttpSetup.signing(profile.attempt);
        try(var context=E2aCrossSourceHttpIT.start(profile)) {
            new TemporaryConfirmationChecks((WebApplicationContext)context).verify();
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Internal real decisions; HTTP intake/read; production operations remain absent\n");
    }
}
