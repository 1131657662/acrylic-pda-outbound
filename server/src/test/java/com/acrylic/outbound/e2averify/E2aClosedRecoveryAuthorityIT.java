package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.RecoveryClosedDatabaseChecks;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aClosedRecoveryAuthorityIT {
    @Test void closesRecoveryTransactionsAndPreservesNonemptyFactsAndHttpRefusals() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker")); profile.migrate(jdbc, "34");
        E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage")); profile.migrate(jdbc);
        var snapshots = new E2aMigrationSnapshot(jdbc, profile.attempt, profile.attempt.resolve("storage"));
        var before = snapshots.capture();
        RecoveryClosedDatabaseChecks.verify(jdbc, profile.attempt);
        try (var context = E2aCrossSourceHttpIT.start(profile, "--outbound.signing.private-key=", "--outbound.signing.public-key=")) {
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:19434/api/v1/pda/orders")).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(401);
            var body = SubmissionJson.parse(response.body());
            assertThat(body.path("code").asText()).isEqualTo("UNAUTHENTICATED");
            assertThat(body.path("serviceMode").asText()).isEqualTo("RECOVERY_PENDING");
            Files.writeString(profile.attempt.resolve("closed-http-response.json"), response.body());
        }
        var after = snapshots.capture();
        before.path("tables").fields().forEachRemaining(entry -> {
            String table = entry.getKey();
            if (!table.equals("server_state") && !table.equals("recovery_installation_binding") && !table.startsWith("recovery_control_"))
                assertThat(after.path("tables").path(table)).as(table).isEqualTo(entry.getValue());
        });
        assertThat(after.path("files")).isEqualTo(before.path("files"));
        Files.writeString(profile.attempt.resolve("closed-before.json"), before.toPrettyString());
        Files.writeString(profile.attempt.resolve("closed-after.json"), after.toPrettyString());
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Synthetic closed authority + real MySQL probe transactions + unchanged nonempty business rows + actual HTTP unauthenticated refusal; no signature/reconstruction/release\n");
    }
}
