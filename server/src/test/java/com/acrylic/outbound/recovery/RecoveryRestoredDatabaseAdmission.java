package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Owned synthetic authority over a complete restored snapshot; never inserts admission model rows. */
public final class RecoveryRestoredDatabaseAdmission {
    private final RecoveryTransactionFixture control;
    private final JdbcTemplate jdbc;
    private final Path home;
    private final long baseline;
    private final RecoveryClosedAuthority.Source source;

    public RecoveryRestoredDatabaseAdmission(DataSource target, Path attempt, Map<String, String> originalSeed)
            throws Exception {
        jdbc = new JdbcTemplate(target);
        home = Files.createDirectory(attempt.resolve("private/restored-db-admission"));
        baseline = jdbc.queryForObject("SELECT recovery_epoch FROM server_state WHERE id=1", Long.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recovery_installation_binding", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recovery_normal_admission", Long.class)).isZero();
        source = new RecoveryClosedAuthority.Source(RecoveryControlFormat.sha256(
            Files.readAllBytes(attempt.resolve("private/archive-snapshot.sql"))), identity(originalSeed));
        save("source-input.json", Map.of("source", source, "baseline", baseline,
            "schema", jdbc.queryForObject("SELECT DATABASE()", String.class),
            "syntheticAuthority", true, "restoredSourceWasUnbound", true));
        control = new RecoveryTransactionFixture(home.resolve("projection"),
            List.of(attempt.resolve("data"), attempt.resolve("restored/storage")), home.resolve("source-input.json"));
    }

    public void register() throws Exception {
        project("QUIESCING");
        for (String phase : List.of("RECOVERING", "VERIFYING", "RELEASE_READY")) {
            control.advancePhase(phase);
            project(phase);
        }
        intent();
        var registration = new RecoveryNormalRegistration(jdbc.getDataSource(), control.location,
            control.checkpoint, baseline);
        var result = registration.register();
        assertThat(result.outcome()).isEqualTo("REGISTERED");
        assertThat(result.ordinaryWritesAllowed()).isFalse();
        assertThat(registration.register().outcome()).isEqualTo("ALREADY_REGISTERED");
        assertThat(verifier().admitted()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recovery_control_projection", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recovery_control_epoch", Long.class)).isEqualTo(2);
        save("registered.json", result);
        save("admitted.json", verifier());
    }

    private void project(String phase) throws Exception {
        var supplied = new RecoveryClosedTestSupply(control.location, control.checkpoint, source);
        var projection = new RecoveryClosedProjection(jdbc.getDataSource(), supplied.authority(), baseline);
        var result = projection.project();
        assertThat(result.phase()).isEqualTo(phase);
        assertThat(result.ordinaryWritesAllowed()).isFalse();
        assertThat(result.localDeletionAllowed()).isFalse();
        assertThat(projection.project()).isEqualTo(result);
        assertThat(verifier().admitted()).isFalse();
        save("projected-" + phase.toLowerCase(Locale.ROOT) + ".json", result);
    }

    public String[] options() {
        return new String[]{"--outbound.recovery.admission.enabled=true",
            "--outbound.recovery.baseline=" + baseline,
            "--outbound.recovery.root=" + control.location.root(),
            "--outbound.recovery.installation-id=" + control.location.installation(),
            "--outbound.recovery.restore-roots[0]=" + control.location.restoreRoots().get(0),
            "--outbound.recovery.restore-roots[1]=" + control.location.restoreRoots().get(1),
            "--outbound.recovery.checkpoint=" + control.checkpoint};
    }

    public Map<String, String> originals() throws Exception {
        var result = new TreeMap<String, String>();
        try (var files = Files.walk(home)) {
            for (Path path : files.filter(Files::isRegularFile).sorted().toList())
                result.put(home.relativize(path).toString(), RecoveryControlFormat.sha256(Files.readAllBytes(path)));
        }
        return result;
    }

    private RecoveryAdmissionVerifier.Result verifier() {
        return RecoveryAdmissionVerifier.of(new RecoveryAdmissionQueries(jdbc.getDataSource()),
            new RecoveryControlReader(control.location), control.checkpoint, control.location, baseline).verify();
    }

    private void intent() throws Exception {
        var head = new RecoveryControlReader(control.location).read(control.checkpoint);
        var intent = RecoveryControlFixture.JSON.createObjectNode().put("formatVersion", 1)
            .put("installationId", head.installationId().toString())
            .put("expectedDatabaseSchema", jdbc.queryForObject("SELECT DATABASE()", String.class))
            .put("expectedBaselineNumericEpoch", baseline).put("controlRevision", Long.parseLong(head.revision()))
            .put("controlRecordSha256", head.recordSha256()).put("previousRecordSha256", head.previousRecordSha256())
            .put("operatorId", "synthetic-restored-admission").put("requestedAt", "2026-10-23T00:00:00Z");
        intent.put("intentSha256", RecoveryControlFormat.hash(intent));
        Files.writeString(control.location.root().resolve(RecoveryNormalIntent.FILE),
            RecoveryControlFormat.canonical(intent), StandardOpenOption.CREATE_NEW);
    }

    private static RecoverySourceIdentity identity(Map<String, String> seed) {
        return new RecoverySourceIdentity("TEMP", id(seed, "warehouseId"), id(seed, "mappedScopeId"),
            id(seed, "mappedSubmissionId"), id(seed, "deviceId"), id(seed, "operatorId"),
            id(seed, "authorizationId"), id(seed, "orderId"), id(seed, "deviceId"),
            id(seed, "mappedClientOrderId"), "1", 1, 2, 1, 0);
    }

    private static UUID id(Map<String, String> seed, String key) { return UUID.fromString(seed.get(key)); }

    private void save(String name, Object value) throws Exception {
        Files.writeString(home.resolve(name), RecoveryControlFixture.JSON.writeValueAsString(value),
            StandardOpenOption.CREATE_NEW);
    }
}
