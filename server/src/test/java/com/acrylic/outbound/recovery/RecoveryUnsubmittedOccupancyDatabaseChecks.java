package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Actual committed SQL source, full-table/file preservation and offline independently pinned consumption. */
public final class RecoveryUnsubmittedOccupancyDatabaseChecks {
    public static void verify(JdbcTemplate jdbc, Path attempt, Map<String,String> seed) throws Exception {
        var f = RecoveryUnsubmittedOccupancyDatabaseSeed.seed(jdbc, seed, attempt.resolve("storage"));
        var capture = new RecoveryUnsubmittedOccupancyCapture(jdbc.getDataSource());
        var request = new RecoveryUnsubmittedOccupancyCapture.Request(f.database, f.initial.expected(), f.initial.source().original("payload"), f.evidence.request());
        var snapshots = new E2aMigrationSnapshot(jdbc, attempt, attempt.resolve("storage")); var before = snapshots.capture();
        Files.writeString(attempt.resolve("unsubmitted-before.json"), before.toPrettyString(), StandardOpenOption.CREATE_NEW);
        var observation = capture.capture(request); consume(f, observation, attempt);
        assertThat(snapshots.capture()).isEqualTo(before);
        Files.writeString(attempt.resolve("unsubmitted-after-capture.json"), snapshots.capture().toPrettyString(), StandardOpenOption.CREATE_NEW);
        refusals(jdbc, capture, request); databaseRefusals(new Probes(jdbc, capture, request, snapshots), f.setId);
        assertThat(snapshots.capture()).isEqualTo(before);
        Files.writeString(attempt.resolve("unsubmitted-after.json"), snapshots.capture().toPrettyString(), StandardOpenOption.CREATE_NEW);
        Files.writeString(attempt.resolve("unsubmitted-checks.txt"),
            "PASS actual original NORMAL instance/null-payload ABORTED/full E2A set+object descriptors/SOURCE_RESERVED/all owner columns/native signature/offline pin/full-table+file preservation/refusal probes; synthetic IDs/development keys, no actual business restore\n");
    }

    private static void consume(RecoveryUnsubmittedOccupancyFixture f, RecoveryUnsubmittedOccupancyCapture.Observation observation, Path attempt) throws Exception {
        var source = f.initial.state.authority.source; byte[] bytes = observation.originalBytes();
        Files.write(attempt.resolve("unsubmitted-observation.json"), bytes, StandardOpenOption.CREATE_NEW);
        Files.write(source.materials.resolve(observation.originalSha256() + ".json"), bytes, StandardOpenOption.CREATE_NEW);
        var proof = f.reader().read(new RecoveryUnsubmittedOccupancy.Request(source.materials, observation.originalSha256(), f.database,
            observation.observedAt()), f.initial.source(), f.evidence.request());
        assertThat(proof.occupancy().rows()).hasSize(1); assertThat(proof.occupancy().sourceEvidenceSetId()).isEqualTo(f.setId);
        assertThat(proof.ordinaryWritesAllowed()).isFalse(); assertThat(proof.localDeletionAllowed()).isFalse();
        Files.writeString(attempt.resolve("unsubmitted-observation.pin"), observation.originalSha256(), StandardOpenOption.CREATE_NEW);
        Files.writeString(attempt.resolve("unsubmitted-material-root.txt"), source.materials.toString(), StandardOpenOption.CREATE_NEW);
        Files.writeString(attempt.resolve("unsubmitted-rsa-public.txt"), Base64.getEncoder().encodeToString(f.initial.state.authority.key.pair.getPublic().getEncoded()), StandardOpenOption.CREATE_NEW);
        Files.writeString(attempt.resolve("unsubmitted-ec-public.txt"), Base64.getEncoder().encodeToString(f.evidence.key.getPublic().getEncoded()), StandardOpenOption.CREATE_NEW);
        for (String kind : List.of("payload", "result", "occupancy")) Files.write(attempt.resolve("unsubmitted-original-" + kind + ".json"), f.initial.source().original(kind), StandardOpenOption.CREATE_NEW);
        Files.write(attempt.resolve("unsubmitted-e2a-open.json"), f.evidence.request().bundle().openBytes(), StandardOpenOption.CREATE_NEW);
        Files.write(attempt.resolve("unsubmitted-e2a-stop.json"), f.evidence.request().bundle().stopBytes(), StandardOpenOption.CREATE_NEW);
        int page = 0;
        for (byte[] value : f.evidence.request().bundle().pageBytes()) Files.write(attempt.resolve("unsubmitted-e2a-page-" + (++page) + ".json"), value, StandardOpenOption.CREATE_NEW);
        bytes[0] ^= 1; assertThat(observation.originalBytes()).isNotEqualTo(bytes);
    }

    private static void refusals(JdbcTemplate jdbc, RecoveryUnsubmittedOccupancyCapture capture, RecoveryUnsubmittedOccupancyCapture.Request request) {
        var database = request.database();
        var wrong = new RecoveryDeviceRegistrationCapture.Database(UUID.randomUUID(), database.schema(), database.sourceRecoveryEpoch());
        assertThatThrownBy(() -> capture.capture(new RecoveryUnsubmittedOccupancyCapture.Request(wrong, request.identity(), request.originalPayload(), request.evidence())))
            .hasMessage("RECOVERY_UNSUBMITTED_DATABASE");
        new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).execute(status -> {
            assertThatThrownBy(() -> capture.capture(request)).hasMessage("RECOVERY_UNSUBMITTED_TRANSACTION"); return null;
        });
        jdbc.update("UPDATE server_state SET service_mode='RECOVERY_PENDING' WHERE id=1");
        try { assertThatThrownBy(() -> capture.capture(request)).hasMessage("RECOVERY_UNSUBMITTED_DATABASE"); }
        finally { jdbc.update("UPDATE server_state SET service_mode='NORMAL' WHERE id=1"); }
        assertThatThrownBy(() -> new RecoveryOriginalOccupancyCapture(jdbc.getDataSource()).capture(
            new RecoveryOriginalOccupancyCapture.Request(database, request.identity(), request.originalPayload()))).isInstanceOf(RuntimeException.class);
    }

    private static void databaseRefusals(Probes context, UUID set) throws Exception {
        var jdbc = context.jdbc(); var capture = context.capture(); var request = context.request(); var snapshots = context.snapshots();
        String setId = set.toString(), bale = SubmissionJson.parse(request.originalPayload()).at("/scans/0/baleId").asText();
        var probes = List.of(
            new Probe("UPDATE source_evidence_set SET audit_through_seq=audit_through_seq+1 WHERE id=UUID_TO_BIN(?)",
                "UPDATE source_evidence_set SET audit_through_seq=audit_through_seq-1 WHERE id=UUID_TO_BIN(?)", setId, "RECOVERY_UNSUBMITTED_EVIDENCE"),
            new Probe("UPDATE source_evidence_item SET original_device_seq=original_device_seq+1 WHERE set_id=UUID_TO_BIN(?) AND item_kind='SCAN'",
                "UPDATE source_evidence_item SET original_device_seq=original_device_seq-1 WHERE set_id=UUID_TO_BIN(?) AND item_kind='SCAN'", setId, "RECOVERY_UNSUBMITTED_EVIDENCE"),
            new Probe("UPDATE source_evidence_object SET byte_count=byte_count+1 WHERE id=(SELECT stop_object_id FROM source_evidence_set WHERE id=UUID_TO_BIN(?))",
                "UPDATE source_evidence_object SET byte_count=byte_count-1 WHERE id=(SELECT stop_object_id FROM source_evidence_set WHERE id=UUID_TO_BIN(?))", setId, "RECOVERY_UNSUBMITTED_STOP_OBJECT"),
            new Probe("UPDATE bale SET dispatch_cycle=dispatch_cycle+1 WHERE id=UUID_TO_BIN(?)",
                "UPDATE bale SET dispatch_cycle=dispatch_cycle-1 WHERE id=UUID_TO_BIN(?)", bale, "RECOVERY_UNSUBMITTED_OWNER"),
            new Probe("UPDATE inventory_position SET upstream_present=0 WHERE bale_id=UUID_TO_BIN(?)",
                "UPDATE inventory_position SET upstream_present=1 WHERE bale_id=UUID_TO_BIN(?)", bale, "RECOVERY_UNSUBMITTED_UNAVAILABLE"));
        for (var probe : probes) {
            jdbc.update(probe.apply(), probe.id()); var changed = snapshots.capture();
            try { assertThatThrownBy(() -> capture.capture(request)).hasMessage(probe.refusal()); assertThat(snapshots.capture()).isEqualTo(changed); }
            finally { jdbc.update(probe.undo(), probe.id()); }
        }
    }
    private record Probe(String apply, String undo, String id, String refusal) { }
    private record Probes(JdbcTemplate jdbc, RecoveryUnsubmittedOccupancyCapture capture,
            RecoveryUnsubmittedOccupancyCapture.Request request, E2aMigrationSnapshot snapshots) { }
}
