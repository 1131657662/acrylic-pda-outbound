package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** A real first-round LOCAL freeze/abort/STOP does not invent a canonical order or handover qualification. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aHandoverNoCanonicalAndroidIT {
    @Test void originalLocalStopWithoutCanonicalCannotRequestHandover() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));profile.migrate(jdbc);
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try(var app=E2aCrossSourceHttpIT.start(profile,E2aHandoverTemporarySubmissionAndroidIT.options(false))) {
            var web=(WebApplicationContext)app;E2aAndroidPreparation.write(web,profile.attempt.resolve("private"));
            var input=profile.attempt.resolve("private/android-http-input.json");
            var contract=(ObjectNode)parse(Files.readAllBytes(input));contract.put("abortBeforePayload",true);
            Files.writeString(input,canonical(contract));E2aAndroidProcess.run(profile.attempt);
            HandoverTemporaryNoCanonicalChecks.verify(web,profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Actual first-round Android freeze/abort/LOCAL E2A STOP; canonical remains absent and HTTP handover context/REQUEST reject with full fact conservation. Synthetic identities/development keys.\n");
    }
}
