package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Native persisted rows and exact command precision, measured against all actual database tables/private files. */
public final class RecoveryTemporaryPreparationDatabaseChecks {
    public static void verify(JdbcTemplate jdbc, Path attempt, Map<String,String> seed) throws Exception {
        var f = RecoveryTemporaryPreparationDatabaseSeed.seed(jdbc, seed); var id = f.baseline.prep.state.authority.source.identity;
        var database = new RecoveryDeviceRegistrationCapture.Database(UUID.fromString(jdbc.queryForObject("SELECT @@server_uuid", String.class)),
            jdbc.queryForObject("SELECT DATABASE()", String.class), jdbc.queryForObject("SELECT recovery_epoch FROM server_state WHERE id=1", String.class));
        var request = new RecoveryTemporaryPreparationCapture.Request(database, id, f.command.toString());
        var capture = new RecoveryTemporaryPreparationCapture(jdbc.getDataSource());
        var snapshots = new E2aMigrationSnapshot(jdbc, attempt, attempt.resolve("storage")); var before = snapshots.capture();
        Files.writeString(attempt.resolve("preparation-before.json"), before.toPrettyString(), StandardOpenOption.CREATE_NEW);
        var observation = capture.capture(request);
        Files.writeString(attempt.resolve("preparation-after-capture.json"), snapshots.capture().toPrettyString(), StandardOpenOption.CREATE_NEW);
        consume(f, new Consumption(request, observation, attempt));
        refusals(jdbc, capture, request); assertThat(snapshots.capture()).isEqualTo(before);
        Files.writeString(attempt.resolve("preparation-after.json"), snapshots.capture().toPrettyString(), StandardOpenOption.CREATE_NEW);
        Files.writeString(attempt.resolve("preparation-checks.txt"),
            "PASS actual instance/time/TEMP original five compressed parts/token/manifest/signedACK/nanosecond command digest/offline independent pin/nonempty all-table+file preservation; synthetic identity/development RSA, no business restoration\n");
    }

    private static void consume(RecoveryTemporaryActivationFixture f, Consumption context) throws Exception {
        var source = f.baseline.prep.state.authority.source; var observation = context.observation(); var attempt = context.attempt();
        byte[] metadata = observation.originalMetadata(), origin = observation.originalOrigin();
        assertThat(origin).containsExactly(source.read().original("origin"));
        Files.write(attempt.resolve("preparation-observation.json"), metadata, StandardOpenOption.CREATE_NEW);
        Files.write(attempt.resolve("preparation-origin.json"), origin, StandardOpenOption.CREATE_NEW);
        Files.write(source.materials.resolve(observation.independentSha256() + ".json"), metadata, StandardOpenOption.CREATE_NEW);
        var reader = new RecoveryOriginalTemporaryPreparation(source.control.location(), source.control.checkpoint,
            new RecoveryReceiptKeys(List.of(f.baseline.prep.state.authority.key.pair.getPublic().getEncoded())));
        var proof = reader.read(new RecoveryOriginalTemporaryPreparation.Request(source.materials, observation.independentSha256(),
            context.request().database(), observation.observedAt()), source.read());
        assertThat(proof.activation().activatedAt().toString()).isEqualTo(f.command.path("activatedAt").asText());
        assertThat(proof.activation().baseline().preparation().partHashes()).hasSize(5);
        assertThat(proof.ordinaryWritesAllowed()).isFalse(); assertThat(proof.localDeletionAllowed()).isFalse();
        Files.writeString(attempt.resolve("preparation-observation.pin"), observation.independentSha256(), StandardOpenOption.CREATE_NEW);
        Files.writeString(attempt.resolve("preparation-material-root.txt"), source.materials.toString(), StandardOpenOption.CREATE_NEW);
        Files.writeString(attempt.resolve("preparation-public-key.txt"), Base64.getEncoder().encodeToString(f.baseline.prep.state.authority.key.pair.getPublic().getEncoded()), StandardOpenOption.CREATE_NEW);
        Files.write(attempt.resolve("preparation-payload.json"), source.read().original("payload"), StandardOpenOption.CREATE_NEW);
        metadata[0] ^= 1; origin[0] ^= 1;
        assertThat(observation.originalMetadata()).isNotEqualTo(metadata); assertThat(observation.originalOrigin()).isNotEqualTo(origin);
    }

    private static void refusals(JdbcTemplate jdbc, RecoveryTemporaryPreparationCapture capture, RecoveryTemporaryPreparationCapture.Request request) {
        var db = request.database(); var wrong = new RecoveryDeviceRegistrationCapture.Database(UUID.randomUUID(), db.schema(), db.sourceRecoveryEpoch());
        assertThatThrownBy(() -> capture.capture(new RecoveryTemporaryPreparationCapture.Request(wrong, request.identity(), request.originalAckCommand())))
            .hasMessage("RECOVERY_TEMP_CAPTURE_DATABASE");
        var rounded = request.originalAckCommand().replace(".123456789Z", ".123Z");
        assertThatThrownBy(() -> capture.capture(new RecoveryTemporaryPreparationCapture.Request(db, request.identity(), rounded)))
            .hasMessage("RECOVERY_TEMP_CAPTURE_COMMAND_DIGEST");
        new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).execute(status -> {
            assertThatThrownBy(() -> capture.capture(request)).hasMessage("RECOVERY_TEMP_CAPTURE_TRANSACTION"); return null;
        });
        jdbc.update("UPDATE server_state SET service_mode='RECOVERY_PENDING' WHERE id=1");
        try { assertThatThrownBy(() -> capture.capture(request)).hasMessage("RECOVERY_TEMP_CAPTURE_DATABASE"); }
        finally { jdbc.update("UPDATE server_state SET service_mode='NORMAL' WHERE id=1"); }
    }

    private record Consumption(RecoveryTemporaryPreparationCapture.Request request, RecoveryTemporaryPreparationCapture.Observation observation, Path attempt) { }
}
