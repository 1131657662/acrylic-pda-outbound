package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryShortApprovalConflictHttpChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aShortApprovalConflictHttpIT {
    @Test void shortApprovalCannotClearInventoryOrForeignReservation() throws Exception {
        var profile = new E2aCrossSourceProfile();
        var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34");
        E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc);
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var context = E2aCrossSourceHttpIT.start(profile,
            "--outbound.temporary.commands.enabled=true", "--server.servlet.session.cookie.secure=false")) {
            TemporaryShortApprovalConflictHttpChecks.verify((WebApplicationContext) context, profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Real HTTP short approval and conflict refusal; original receipts and all business tables preserved\n");
    }
}
