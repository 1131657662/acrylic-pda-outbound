package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.HeaderCompensationRestartChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHeaderCompensationRestartIT {
    @Test void newProcessReadsAndReplaysOriginalCompensationWithoutAnotherVersion() throws Exception {
        var profile = E2aCrossSourceProfile.completedHeaderCompensation();
        try (var context = E2aCrossSourceHttpIT.start(profile,
            "--server.servlet.session.cookie.secure=false", "--outbound.corrections.commands.enabled=true")) {
            HeaderCompensationRestartChecks.verify((WebApplicationContext) context, profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("header-restart-completed.marker"),
            "New JVM; original authenticated GET/POST bytes and fixed-public-key signature; no extra business facts");
    }
}
