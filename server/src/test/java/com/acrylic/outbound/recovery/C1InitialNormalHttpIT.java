package com.acrylic.outbound.recovery;

import com.acrylic.c1probe.C1HttpProbe;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Nonempty real MySQL, real Spring HTTP, original bytes, and original connection-entry regression. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class C1InitialNormalHttpIT {
    @Test void independentNormalRegistrationPreservesOriginalsAndRejectsMissingOrConflictingSides() throws Exception {
        var profile = new C1MainProfile(); profile.seedAndMigrate();
        var before = C1OriginalFacts.snapshot(profile.jdbc);
        Files.writeString(profile.attempt.resolve("c1-original-facts-before.json"),
            new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(before));
        var http = new C1HttpChecks(profile);
        String option = "--c1.test.base=" + profile.attempt;
        try (var app = profile.start(C1HttpProbe.class, option)) {
            http.missingSidesAndRegister(); http.missingExternalAndStateConflicts();
        }
        try (var restarted = profile.start(C1HttpProbe.class, option)) {
            http.admitted(); http.closedBootstrapDenies();
        }
        C1LostCommitCheck.verify(profile);
        C1EntryRegression.verify(profile);
        var after = C1OriginalFacts.snapshot(profile.jdbc);
        Files.writeString(profile.attempt.resolve("c1-original-facts-after.json"),
            new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(after));
        assertThat(after).isEqualTo(before);
        assertThat(profile.jdbc.queryForObject("SELECT COUNT(*) FROM recovery_installation_binding", Integer.class)).isZero();
        assertThat(profile.jdbc.queryForObject("SELECT COUNT(*) FROM recovery_control_epoch", Integer.class)).isZero();
        assertThat(profile.jdbc.queryForObject("SELECT COUNT(*) FROM recovery_control_projection", Integer.class)).isZero();
        assertThat(profile.jdbc.queryForObject("SELECT COUNT(*) FROM recovery_normal_admission", Integer.class)).isZero();
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "C1 true HTTP+MySQL initial NORMAL; original facts unchanged; missing sides deny; restart; "
            + "closed bootstrap denies; real lost-commit acknowledgement fences; all attempt105 entry checks reused.\n");
    }
}
