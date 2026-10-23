package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.*;

/** Actual verifier and connection-entry observations on the same physical connection, without DML. */
final class RecoverySuccessorAdmissionChecks {
    private RecoverySuccessorAdmissionChecks() { }

    static void verify(JdbcTemplate original, JdbcTemplate target, RecoverySuccessorDatabaseFixture fixture,
            Path evidence) throws Exception {
        Files.createDirectory(evidence);
        var before = RecoverySuccessorDatabaseChecks.snapshot(target);
        var files = ClosedAdmissionIdentityObservation.files(fixture.root());
        try (var physical = target.getDataSource().getConnection()) {
            var single = new SingleConnectionDataSource(physical, true); var jdbc = new JdbcTemplate(single);
            var status = counters(jdbc); var binlog = jdbc.queryForList("SHOW MASTER STATUS");
            var result = fixture.runtimeResult(jdbc);
            assertThat(result.admitted()).as(result.gaps().toString()).isTrue();
            assertThat(result.gaps()).isEmpty();
            assertThat(result.ordinaryWritesAllowed()).isFalse(); assertThat(result.localDeletionAllowed()).isFalse();
            assertThat(result.controlRevision()).isEqualTo(fixture.head().path("revision").asText());
            var observed = assertReadOnly(jdbc, status, binlog);
            new RecoveryClosedConnectionAdmission(fixture.location(), fixture.checkpoint(), fixture.baseline()).verify(physical);
            assertReadOnly(jdbc, observed, binlog);
            save(evidence.resolve("admitted.json"), Map.of("result", result, "selectDeltaPerEntry", 1, "dml", 0, "binlogUnchanged", true));
            var external = RecoveryDatabaseControl.readOnly(fixture.location(), new RecoveryControlReader(fixture.location()).read(fixture.checkpoint()));
            RecoverySuccessorObservationChecks.verify(jdbc, external, fixture.baseline(), evidence);
        }
        var sourceBefore = RecoverySuccessorDatabaseChecks.snapshot(original);
        var denied = fixture.runtimeResult(original);
        assertThat(denied.admitted()).isFalse(); assertThat(denied.gaps()).containsExactly("DATABASE_EVIDENCE_UNAVAILABLE");
        assertThat(RecoverySuccessorDatabaseChecks.snapshot(original)).isEqualTo(sourceBefore);
        save(evidence.resolve("source-denied.json"), denied);
        missingIntent(target, fixture, evidence);
        assertThat(RecoverySuccessorDatabaseChecks.snapshot(target)).isEqualTo(before);
        assertThat(ClosedAdmissionIdentityObservation.files(fixture.root())).isEqualTo(files);
        save(evidence.resolve("conservation.json"), Map.of("tables", before, "files", files));
    }

    private static void missingIntent(JdbcTemplate jdbc, RecoverySuccessorDatabaseFixture fixture, Path evidence) throws Exception {
        long revision = fixture.head().path("revision").asLong();
        var external = RecoveryDatabaseControl.readOnly(fixture.location(), new RecoveryControlReader(fixture.location()).read(fixture.checkpoint()));
        var intent = external.normal(revision);
        Path normal = fixture.root().resolve("normal-history/" + intent.intentSha256() + ".json");
        Path copy = evidence.resolve("missing-current-intent.original.json"); Files.copy(normal, copy);
        String original = RecoveryControlFormat.sha256(Files.readAllBytes(normal));
        save(evidence.resolve("missing-input.json"), Map.of("original", normal.toString(), "sha256", original, "copy", copy.toString()));
        Path detached = evidence.resolve("temporarily-detached-intent.json"); Files.move(normal, detached);
        try {
            var denied = fixture.runtimeResult(jdbc);
            assertThat(denied.admitted()).isFalse(); assertThat(denied.gaps()).containsExactly("DURABLE_INTENT_UNAVAILABLE");
            save(evidence.resolve("missing-intent-denied.json"), denied);
        } finally { Files.move(detached, normal); }
        assertThat(RecoveryControlFormat.sha256(Files.readAllBytes(normal))).isEqualTo(original);
        assertThat(fixture.runtimeResult(jdbc).admitted()).isTrue();
    }

    private static Map<String, Long> assertReadOnly(JdbcTemplate jdbc, Map<String, Long> before, List<Map<String, Object>> binlog) {
        var after = counters(jdbc);
        assertThat(after.get("Com_select") - before.get("Com_select")).isEqualTo(1);
        for (String command : List.of("Com_insert", "Com_update", "Com_delete", "Com_replace"))
            assertThat(after.get(command)).as(command).isEqualTo(before.get(command));
        assertThat(jdbc.queryForList("SHOW MASTER STATUS")).isEqualTo(binlog); return after;
    }

    private static Map<String, Long> counters(JdbcTemplate jdbc) {
        return jdbc.query("SHOW SESSION STATUS WHERE Variable_name IN ('Com_select','Com_insert','Com_update','Com_delete','Com_replace')",
            rs -> { var result = new TreeMap<String, Long>(); while (rs.next()) result.put(rs.getString(1), rs.getLong(2)); return result; });
    }

    static void save(Path path, Object value) throws Exception {
        Files.writeString(path, RecoveryControlFixture.JSON.writeValueAsString(value), StandardOpenOption.CREATE_NEW);
    }
}
