package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryExclusionHttpChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

/** Synthetic identities in a nonempty isolated database, with real HTTP/Session/CSRF. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aTemporaryExclusionHttpIT {
    @Test void signsExclusionsWithoutChangingCandidateOrStockFacts() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));profile.migrate(jdbc);
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try(var context=E2aCrossSourceHttpIT.start(profile,"--outbound.temporary.commands.enabled=true","--outbound.recall.fault-withdrawal-enabled=true")) {
            new TemporaryExclusionHttpChecks((WebApplicationContext)context).verify(profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),"HTTP exclusion/replay/permission/self/stock/drift/concurrency/final-SQL verified\n");
    }
}
