package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.handover.HandoverReadController;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aHandoverReadHttpIT {
    @Test void contextsDriveCommandsAndReadsRecoverOriginalBytesWithoutBusinessWrites() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "52"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var app = E2aCrossSourceHttpIT.start(profile, "--server.servlet.session.cookie.secure=false")) {
            assertThat(app.getBeansOfType(HandoverReadController.class)).isEmpty();
        }
        String[] options = {"--server.servlet.session.cookie.secure=false", "--outbound.handover.commands.enabled=true"};
        HandoverReadChecks checks;
        try (var app = E2aCrossSourceHttpIT.start(profile, options)) {
            assertThat(app.getBeansOfType(HandoverReadController.class)).hasSize(1);
            checks = new HandoverReadChecks(app, profile.attempt); checks.verify();
        }
        try (var app = E2aCrossSourceHttpIT.start(profile, options)) { checks.restart(app); }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "HTTP GET contexts -> REQUEST/APPROVE/PREPARE/ACTIVATE; exact originals/restart; sixteen business tables unchanged by reads");
    }
}
