package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.E2aAndroidPreparation;
import com.acrylic.outbound.inventory.TemporaryVerifiedDispositionChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

/** Real Android intake precedes the independently authenticated supervisor disposition. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aVerifiedDispositionHttpIT {
    @Test void trustedAndroidStopAllowsRejectedSourceRelease() throws Exception {
        run(false);
    }

    static void run(boolean close) throws Exception {
        var profile = new E2aCrossSourceProfile();
        var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34");
        E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "59");
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var context = E2aCrossSourceHttpIT.start(profile,
            "--outbound.temporary.commands.enabled=true", "--server.servlet.session.cookie.secure=false")) {
            var web = (WebApplicationContext) context;
            E2aAndroidPreparation.write(web, profile.attempt.resolve("private"));
            E2aAndroidProcess.run(profile.attempt);
            TemporaryVerifiedDispositionChecks.verify(web, profile.attempt, close);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Android trusted STOP/VERIFY -> Web supervisor REJECT -> " + (close ? "CLOSE/QUARANTINE" : "RELEASE")
                + "; original receipts preserved\n");
    }
}
