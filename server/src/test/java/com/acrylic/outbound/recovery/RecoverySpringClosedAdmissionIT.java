package com.acrylic.outbound.recovery;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Full product context/HTTP/Session with synthetic closed registration, not full recovery or P5. */
class RecoverySpringClosedAdmissionIT {
    @Test void initializationAndRealSessionBorrowsConsumeTheClosedPhysicalAdmission() throws Exception {
        var fixture = new SpringClosedAdmissionFixture();
        var originals = ClosedAdmissionIdentityObservation.files(fixture.control.root.getParent());
        try (var context = fixture.start("valid")) {
            var startup = context.getBean(SpringClosedAdmissionProbe.StartupBorrow.class);
            fixture.save("startup-borrow.json", Map.of("connectionId", startup.connectionId(),
                "operationId", startup.operationId(), "kind", "INITIALIZATION"));
            var checks = new SpringClosedAdmissionHttpChecks(fixture, context);
            checks.wiring();
            checks.normalSession();
            checks.rejectPendingRecovery();
        }
        coldRefusal(fixture);
        assertThat(ClosedAdmissionIdentityObservation.files(fixture.control.root.getParent())).isEqualTo(originals);
        fixture.save("control-originals.json", originals);
        fixture.save("result.json", Map.of("springContext", true, "actualProductCsrf", true,
            "jdbcSession", true, "coldInvalidStartupRefused", true, "syntheticClosedRegistration", true));
    }

    private static void coldRefusal(SpringClosedAdmissionFixture fixture) throws Exception {
        fixture.pendingRecovery();
        var before = fixture.tables();
        Throwable failure = catchThrowable(() -> {
            try (var unexpected = fixture.start("invalid")) { fail("invalid closed registration started"); }
        });
        assertThat(failure).hasStackTraceContaining("RECOVERY_DATABASE_ADMISSION_REFUSED:SERVER_MODE");
        assertThat(fixture.tables()).isEqualTo(before);
        fixture.save("invalid-startup.json", Map.of("exception", failure.toString(), "tablesUnchanged", true));
        fixture.restoreOwnedMode();
    }
}
