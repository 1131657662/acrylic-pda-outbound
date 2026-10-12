package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.HeaderCompensationHistoryChecks;
import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aHeaderCompensationHistoryIT {
    @Test void originalHistoricalApprovalWithoutNewHashesCanBeCompensated() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connectHistoricalHeader();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        Flyway.configure().dataSource(jdbc.getDataSource()).load().migrate();
        try (var context = E2aCrossSourceHttpIT.start(profile, "--server.servlet.session.cookie.secure=false",
            "--outbound.corrections.commands.enabled=true")) {
            HeaderCompensationHistoryChecks.verify((WebApplicationContext) context, profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Original archived 847 database/private files/CREATE/APPROVE/public key; nonempty upgrade and new compensating version");
    }
}
