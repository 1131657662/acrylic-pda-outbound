package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Real database rejection/rollback checks on a restored prefix with original immutable evidence. */
public final class RecoverySuccessorDatabaseChecks {
    private RecoverySuccessorDatabaseChecks() { }

    public static void verify(JdbcTemplate source, JdbcTemplate target, RecoverySuccessorDatabaseFixture fixture,
            Path evidence) throws Exception {
        var pins = fixture.pins(source);
        Files.writeString(evidence.resolve("source-pins.json"), RecoveryControlFixture.JSON.writeValueAsString(pins), StandardOpenOption.CREATE_NEW);
        rejectPins(target, fixture, pins, evidence);
        fixture.successor(target, pins, Map.of());
        reject(source, () -> fixture.project(source), "CONTROL_DATABASE_INSTANCE_MISMATCH");
        rollbackBinding(target, fixture);
        var projected = fixture.project(target);
        assertThat(projected.phase()).isEqualTo("QUIESCING");
        assertThat(projected.numericEpoch()).isEqualTo(Long.toString(fixture.baseline() + 1));
        assertThat(fixture.project(target)).isEqualTo(projected);
        release(target, fixture, evidence, "round-two");
        RecoverySuccessorAdmissionChecks.verify(source, target, fixture, evidence.resolve("round-two-admission"));
        var nextPins = fixture.pins(target);
        assertThat(nextPins.sourceBindingSha256()).isNotEqualTo(pins.sourceBindingSha256());
        assertThat(nextPins.sourceNormalSha256()).isNotEqualTo(pins.sourceNormalSha256());
        fixture.successor(target, nextPins, Map.of()); fixture.project(target);
        release(target, fixture, evidence, "round-three");
        immutable(target);
        assertThat(target.queryForObject("SELECT COUNT(*) FROM recovery_successor_binding", Integer.class)).isEqualTo(2);
        assertThat(target.queryForObject("SELECT COUNT(*) FROM recovery_successor_normal", Integer.class)).isEqualTo(2);
        assertThat(target.queryForObject("SELECT COUNT(*) FROM recovery_control_projection", Integer.class)).isEqualTo(12);
        assertThat(target.queryForObject("SELECT COUNT(*) FROM recovery_control_epoch", Integer.class)).isEqualTo(4);
        assertThat(target.queryForObject("SELECT recovery_epoch FROM server_state WHERE id=1", Long.class)).isEqualTo(fixture.baseline() + 3);
        RecoverySuccessorAdmissionChecks.verify(source, target, fixture, evidence.resolve("round-three-admission"));
    }

    private static void rejectPins(JdbcTemplate target, RecoverySuccessorDatabaseFixture fixture,
            RecoveryDatabaseSource.Pins pins, Path evidence) throws Exception {
        var cases = new LinkedHashMap<String, String>();
        cases.put("sourceBindingSha256", "d".repeat(64)); cases.put("sourceNormalSha256", "e".repeat(64));
        cases.put("targetServerUuid", UUID.randomUUID().toString()); cases.put("targetDatabaseSchema", "synthetic_wrong_schema");
        for (var entry : cases.entrySet()) {
            var branch = fixture.fork(evidence.resolve("negative-" + entry.getKey()));
            branch.successor(target, pins, Map.of(entry.getKey(), entry.getValue()));
            String code = entry.getKey().startsWith("source") ? "CONTROL_SUCCESSOR_SOURCE_PIN_MISMATCH" : "CONTROL_DATABASE_INSTANCE_MISMATCH";
            reject(target, () -> branch.project(target), code);
        }
    }

    private static void rollbackBinding(JdbcTemplate jdbc, RecoverySuccessorDatabaseFixture fixture) throws Exception {
        jdbc.execute("CREATE TRIGGER successor_test_binding_failure BEFORE INSERT ON recovery_successor_binding FOR EACH ROW "
            + "BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic last binding SQL failure'; END");
        try { rollback(jdbc, () -> fixture.project(jdbc)); }
        finally { jdbc.execute("DROP TRIGGER successor_test_binding_failure"); }
    }

    private static void release(JdbcTemplate jdbc, RecoverySuccessorDatabaseFixture fixture, Path evidence, String label) throws Exception {
        for (String phase : List.of("RECOVERING", "VERIFYING", "RELEASE_READY")) {
            fixture.advance(phase); var projected = fixture.project(jdbc);
            assertThat(fixture.project(jdbc)).isEqualTo(projected);
            assertThat(projected.ordinaryWritesAllowed()).isFalse(); assertThat(projected.localDeletionAllowed()).isFalse();
        }
        reject(jdbc, () -> fixture.admit(jdbc), "CONTROL_SUCCESSOR_NORMAL_REQUIRED");
        Path normal = fixture.writeNormal(jdbc); String original = RecoveryControlFormat.sha256(Files.readAllBytes(normal));
        jdbc.execute("CREATE TRIGGER successor_test_normal_failure BEFORE UPDATE ON server_state FOR EACH ROW BEGIN "
            + "IF NEW.service_mode='NORMAL' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic last NORMAL SQL failure'; END IF; END");
        try { rollback(jdbc, () -> fixture.admit(jdbc)); }
        finally { jdbc.execute("DROP TRIGGER successor_test_normal_failure"); }
        var result = fixture.admit(jdbc); assertThat(result.outcome()).isEqualTo("REGISTERED");
        var beforeReplay = snapshot(jdbc); assertThat(fixture.admit(jdbc).outcome()).isEqualTo("ALREADY_REGISTERED");
        assertThat(snapshot(jdbc)).isEqualTo(beforeReplay);
        assertThat(RecoveryControlFormat.sha256(Files.readAllBytes(normal))).isEqualTo(original);
        assertThat(result.ordinaryWritesAllowed()).isFalse(); assertThat(result.localDeletionAllowed()).isFalse();
        Files.writeString(evidence.resolve(label + "-normal.json"), RecoveryControlFixture.JSON.writeValueAsString(result), StandardOpenOption.CREATE_NEW);
    }

    private static void immutable(JdbcTemplate jdbc) {
        for (String table : List.of("recovery_installation_binding", "recovery_normal_admission", "recovery_successor_binding", "recovery_successor_normal")) {
            var before = snapshot(jdbc);
            assertThatThrownBy(() -> jdbc.update("UPDATE " + table + " SET installation_id=installation_id"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("DELETE FROM " + table)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(snapshot(jdbc)).isEqualTo(before);
        }
    }

    private static void reject(JdbcTemplate jdbc, org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
        var before = snapshot(jdbc);
        assertThatThrownBy(action).isInstanceOf(RecoveryControlException.class).hasMessage(code);
        assertThat(snapshot(jdbc)).isEqualTo(before);
    }

    private static void rollback(JdbcTemplate jdbc, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        var before = snapshot(jdbc);
        assertThatThrownBy(action).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(snapshot(jdbc)).isEqualTo(before);
    }

    public static Map<String, String> snapshot(JdbcTemplate jdbc) {
        var values = new TreeMap<String, String>();
        for (String table : List.of("server_state", "recovery_installation_binding", "recovery_normal_admission", "recovery_control_projection",
                "recovery_control_epoch", "recovery_successor_binding", "recovery_successor_normal"))
            values.put(table, RecoveryControlFormat.hash(RecoveryControlFixture.JSON.valueToTree(jdbc.query(
                "SELECT * FROM " + table + " ORDER BY 1,2", (rs, row) -> {
                    var value = RecoveryControlFixture.JSON.createObjectNode();
                    for (int column = 1; column <= rs.getMetaData().getColumnCount(); column++) {
                        Object cell = rs.getObject(column);
                        value.put(rs.getMetaData().getColumnLabel(column), cell instanceof byte[] bytes
                            ? java.util.HexFormat.of().formatHex(bytes) : rs.getString(column));
                    }
                    return value;
                }))));
        return values;
    }
}
