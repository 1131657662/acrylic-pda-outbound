package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionFiles;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aUnsubmittedHttpIT {
    @Test void originalUnsubmittedSourcesAreVerifiedWithoutOwningCurrentTempBales() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34");
        var seed = E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission", Integer.class)).isPositive();
        profile.migrate(jdbc, "37");
        var f = new E2aCrossSourceFixture(jdbc, new SubmissionFiles(profile.attempt.resolve("storage").toString()), seed);
        f.seed(); E2aCrossSourceHttpSetup.signing(profile.attempt);
        Files.createDirectory(profile.attempt.resolve("http"));
        try (var context = E2aCrossSourceHttpIT.start(profile)) {
            var preparation = new E2aUnsubmittedPreparation(f, context);
            var records = new E2aUnsubmittedRecords(preparation, context);
            var setup = new E2aCrossSourceHttpSetup(f, records.records, records.events);
            var http = new E2aCrossSourceHttp(new E2aCrossSourceWire(f, setup.items()), setup.token, profile.attempt.resolve("http"));
            new E2aUnsubmittedChecks(http, setup, preparation, context).verify();
            E2aCrossSourceExport.write(http.wire, profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Nonempty V34->V37; real HTTP audit/OPEN/RAW/PAGE/STOP/VERIFY/replay; unsubmitted FORMAL/TEMP/pending; no foreign K\n");
    }
}
