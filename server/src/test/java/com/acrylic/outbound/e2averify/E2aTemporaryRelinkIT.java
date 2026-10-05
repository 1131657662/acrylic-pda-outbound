package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryRelinkChecks;
import com.acrylic.outbound.inventory.TemporaryLinkChecks;
import com.acrylic.outbound.inventory.TemporaryLinkConfirmHttpChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

/** Fresh isolated MySQL and real HTTP; no production gate is changed. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aTemporaryRelinkIT {
    @Test void controlledRelinkKeepsHistoryAndRollsBackEveryEffect() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        profile.migrate(jdbc,"latest");E2aCrossSourceHttpSetup.signing(profile.attempt);
        try(var context=E2aCrossSourceHttpIT.start(profile,"--outbound.temporary.commands.enabled=true")) {
            new TemporaryRelinkChecks((WebApplicationContext)context).verify(profile.attempt);
            TemporaryLinkConfirmHttpChecks.verify((WebApplicationContext)context);
        }
        try(var context=E2aCrossSourceHttpIT.start(profile)) {
            new TemporaryLinkChecks((WebApplicationContext)context).verify();
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"Controlled relink HTTP checks completed\n");
    }
}
