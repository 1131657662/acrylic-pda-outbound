package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Reads actual isolated committed columns; unchanged full-table/file snapshots surround every producer call. */
public final class RecoveryOriginalOccupancyDatabaseChecks {
    public static void verify(JdbcTemplate jdbc, Path attempt, Map<String,String> seed) throws Exception {
        var state = RecoveryOriginalOccupancyDatabaseSeed.seed(jdbc, seed);
        var capture = new RecoveryOriginalOccupancyCapture(jdbc.getDataSource());
        var database = new RecoveryDeviceRegistrationCapture.Database(UUID.fromString(jdbc.queryForObject("SELECT @@server_uuid", String.class)),
            jdbc.queryForObject("SELECT DATABASE()", String.class), jdbc.queryForObject("SELECT recovery_epoch FROM server_state WHERE id=1", String.class));
        var request = new RecoveryOriginalOccupancyCapture.Request(database, state.authority.source.identity, state.read().original("payload"));
        var snapshots = new E2aMigrationSnapshot(jdbc, attempt, attempt.resolve("storage")); var before = snapshots.capture();
        Files.writeString(attempt.resolve("occupancy-before.json"), before.toPrettyString(), StandardOpenOption.CREATE_NEW);
        var observation = capture.capture(request);
        Files.writeString(attempt.resolve("occupancy-after-capture.json"), snapshots.capture().toPrettyString(), StandardOpenOption.CREATE_NEW);
        consume(state, request, observation, attempt);
        refusals(jdbc, capture, request); assertThat(snapshots.capture()).isEqualTo(before);
        Files.writeString(attempt.resolve("occupancy-after.json"), snapshots.capture().toPrettyString());
        stockRefusals(jdbc, capture, request, snapshots);
        assertThat(snapshots.capture()).isEqualTo(before);
        Files.writeString(attempt.resolve("occupancy-checks.txt"),
            "PASS actual database/time/source/terminal/payload/every owner column/offline independent pin/nonempty table+file preservation/stock+missing owner refusals; synthetic source/development RSA, no original device or actual business restoration\n");
    }

    private static void consume(RecoverySourceStateFixture state, RecoveryOriginalOccupancyCapture.Request request,
            RecoveryOriginalOccupancyCapture.Observation observation, Path attempt) throws Exception {
        var source = state.authority.source; byte[] bytes = observation.originalBytes();
        Files.write(attempt.resolve("occupancy-observation.json"), bytes, StandardOpenOption.CREATE_NEW);
        Files.write(source.materials.resolve(observation.originalSha256() + ".json"), bytes, StandardOpenOption.CREATE_NEW);
        var reader = new RecoveryOriginalOccupancy(source.control.location(), source.control.checkpoint,
            new RecoveryReceiptKeys(List.of(state.authority.key.pair.getPublic().getEncoded())));
        var proof = reader.read(new RecoveryOriginalOccupancy.Request(source.materials, observation.originalSha256(), request.database(),
            observation.observedAt()), state.read());
        assertThat(proof.occupancy().rows()).hasSize(1);
        assertThat(proof.occupancy().rows().getFirst().baleId().toString()).isEqualTo(state.row().path("baleId").asText());
        assertThat(proof.ordinaryWritesAllowed()).isFalse(); assertThat(proof.localDeletionAllowed()).isFalse();
        Files.writeString(attempt.resolve("occupancy-observation.pin"), observation.originalSha256(), StandardOpenOption.CREATE_NEW);
        Files.writeString(attempt.resolve("occupancy-material-root.txt"), source.materials.toString(), StandardOpenOption.CREATE_NEW);
        Files.writeString(attempt.resolve("occupancy-source-public-key.txt"), Base64.getEncoder().encodeToString(state.authority.key.pair.getPublic().getEncoded()), StandardOpenOption.CREATE_NEW);
        for (String kind : List.of("payload", "result")) Files.write(attempt.resolve("occupancy-original-" + kind + ".json"), state.read().original(kind), StandardOpenOption.CREATE_NEW);
        Files.writeString(attempt.resolve("occupancy-source-rows.json"), SubmissionJson.canonical(state.occupancy), StandardOpenOption.CREATE_NEW);
        bytes[0] ^= 1; assertThat(observation.originalBytes()).isNotEqualTo(bytes);
    }

    private static void refusals(JdbcTemplate jdbc, RecoveryOriginalOccupancyCapture capture, RecoveryOriginalOccupancyCapture.Request request) {
        var db = request.database(); var wrong = new RecoveryDeviceRegistrationCapture.Database(UUID.randomUUID(), db.schema(), db.sourceRecoveryEpoch());
        assertThatThrownBy(() -> capture.capture(new RecoveryOriginalOccupancyCapture.Request(wrong, request.identity(), request.originalPayload())))
            .hasMessage("RECOVERY_OCCUPANCY_CAPTURE_DATABASE");
        byte[] changed = request.originalPayload(); changed[changed.length-2] ^= 1;
        assertThatThrownBy(() -> capture.capture(new RecoveryOriginalOccupancyCapture.Request(db, request.identity(), changed))).isInstanceOf(RuntimeException.class);
        new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).execute(status -> {
            assertThatThrownBy(() -> capture.capture(request)).hasMessage("RECOVERY_OCCUPANCY_CAPTURE_TRANSACTION"); return null;
        });
        jdbc.update("UPDATE server_state SET service_mode='RECOVERY_PENDING' WHERE id=1");
        try { assertThatThrownBy(() -> capture.capture(request)).hasMessage("RECOVERY_OCCUPANCY_CAPTURE_DATABASE"); }
        finally { jdbc.update("UPDATE server_state SET service_mode='NORMAL' WHERE id=1"); }
    }

    private static void stockRefusals(JdbcTemplate jdbc, RecoveryOriginalOccupancyCapture capture,
            RecoveryOriginalOccupancyCapture.Request request, E2aMigrationSnapshot snapshots) throws Exception {
        String bale = SubmissionJson.parse(request.originalPayload()).path("scans").get(0).path("baleId").asText();
        jdbc.update("UPDATE bale SET dispatch_cycle=dispatch_cycle+1 WHERE id=UUID_TO_BIN(?)", bale); var changed = snapshots.capture();
        try { assertThatThrownBy(() -> capture.capture(request)).hasMessage("RECOVERY_OCCUPANCY_CAPTURE_OWNER"); assertThat(snapshots.capture()).isEqualTo(changed); }
        finally { jdbc.update("UPDATE bale SET dispatch_cycle=dispatch_cycle-1 WHERE id=UUID_TO_BIN(?)", bale); }
        jdbc.update("UPDATE bale_commitment SET state='DISPATCHED' WHERE bale_id=UUID_TO_BIN(?)", bale); changed = snapshots.capture();
        try { assertThatThrownBy(() -> capture.capture(request)).hasMessage("RECOVERY_OCCUPANCY_CAPTURE_OWNER"); assertThat(snapshots.capture()).isEqualTo(changed); }
        finally { jdbc.update("UPDATE bale_commitment SET state='REVIEW_RESERVED' WHERE bale_id=UUID_TO_BIN(?)", bale); }
        jdbc.update("UPDATE inventory_position SET blocked_reason='development blocked' WHERE bale_id=UUID_TO_BIN(?)", bale); changed = snapshots.capture();
        try { assertThatThrownBy(() -> capture.capture(request)).hasMessage("RECOVERY_OCCUPANCY_CAPTURE_UNAVAILABLE"); assertThat(snapshots.capture()).isEqualTo(changed); }
        finally { jdbc.update("UPDATE inventory_position SET blocked_reason=NULL WHERE bale_id=UUID_TO_BIN(?)", bale); }
    }
}
