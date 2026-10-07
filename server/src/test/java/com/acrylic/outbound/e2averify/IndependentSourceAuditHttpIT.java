package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionFiles;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** D04: all migrations, actual authenticated audit/intake, and independently retained FORMAL/TEMP provenance. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class IndependentSourceAuditHttpIT {
    @Test void mixedDeviceHistoryPreservesSeparateSourcesAndCannotRelabelOriginalEvents() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");var seed=E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        profile.migrate(jdbc,"latest");
        var fixture=new E2aCrossSourceFixture(jdbc,new SubmissionFiles(profile.attempt.resolve("storage").toString()),seed);
        fixture.seed();E2aCrossSourceHttpSetup.signing(profile.attempt);Files.createDirectory(profile.attempt.resolve("http"));
        try(var context=E2aCrossSourceHttpIT.start(profile)) {
            var preparation=new E2aUnsubmittedPreparation(fixture,context);
            var records=new E2aUnsubmittedRecords(preparation,context);
            var setup=new E2aCrossSourceHttpSetup(fixture,records.records,records.events);
            var http=new E2aCrossSourceHttp(new E2aCrossSourceWire(fixture,setup.items()),setup.token,profile.attempt.resolve("http"));
            new E2aUnsubmittedChecks(http,setup,preparation,context).verify();
            new IndependentSourceAuditChecks(http,setup).verify();
            E2aCrossSourceExport.write(http.wire,profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Synthetic identities; real mixed FORMAL/TEMP audit/OPEN/RAW/PAGE/STOP/VERIFY/replay and SQL failure recovery; separate immutable sources, foreign bales never reserved");
    }
}
