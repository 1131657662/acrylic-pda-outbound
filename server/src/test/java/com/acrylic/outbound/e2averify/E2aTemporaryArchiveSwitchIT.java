package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryArchiveFrontDoorHttpChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolated non-empty MySQL + real HTTP proof of the TEMP archive gate: closed by default (historical hard rejection,
 * nothing archived) and open only when the operator sets {@code outbound.temporary.archive.enabled=true}.
 *
 * <p>The two phases run against two contexts backed by the same database, so the request, session and submission are
 * identical and the only variable is the switch — the same shape used by the handover switch IT.
 */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aTemporaryArchiveSwitchIT {
    @Test void tempArchiveStaysClosedByDefaultAndOpensOnlyWithTheOperatorSwitch() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class)).isPositive();
        profile.migrate(jdbc,"36");E2aCrossSourceHttpSetup.signing(profile.attempt);
        TemporaryArchiveFrontDoorHttpChecks checks;
        try(var closed=E2aCrossSourceHttpIT.start(profile)) {
            checks=TemporaryArchiveFrontDoorHttpChecks.prepare((WebApplicationContext)closed);
            checks.closedByDefault();
        }
        try(var open=E2aCrossSourceHttpIT.start(profile,"--outbound.temporary.archive.enabled=true")) {
            checks.openWhenEnabled((WebApplicationContext)open);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "TEMP archive closed by default (real HTTP 409 TEMPORARY_CONFIRMATION_REQUIRED, no archive row) and open only "
            + "with outbound.temporary.archive.enabled=true (real HTTP 200, signed archiveVersion=2 proof, one row, replay stable)\n");
    }
}
