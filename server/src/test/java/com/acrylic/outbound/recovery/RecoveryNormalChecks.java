package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Real isolated MySQL initial NORMAL admission; no production routes, beans or policy grants. */
public final class RecoveryNormalChecks {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Runs on the same closed-phase control chain the caller already projected to RELEASE_READY. */
    public static void afterReleaseReady(JdbcTemplate jdbc, RecoveryTransactionFixture fixture, Path evidence) throws Exception {
        // The projected numeric epoch is the caller's baseline plus one; never trust a drift-prone reading.
        long baseline = jdbc.queryForObject("SELECT baseline_numeric_epoch FROM recovery_installation_binding WHERE id=1", Long.class);
        Files.deleteIfExists(fixture.location.root().resolve(RecoveryNormalIntent.FILE));
        withoutDurableIntent(jdbc, fixture, baseline, evidence);
        resetAdmission(jdbc);
        withDurableIntent(jdbc, fixture, baseline, evidence);
        Files.writeString(evidence.resolve("normal-admission-checks.txt"),
            "PASS V40 initial NORMAL requires the RELEASE_READY terminal record; durable intent bound to the chain; idempotent; conflicts and missing intent leave RECOVERY_PENDING\n");
    }

    /**
     * Restores the numeric epoch a fault drill changed, so the caller's later verification uses the
     * projected identity again. Only server_state is touched; closed-phase records stay retained.
     */
    public static void restoreEpoch(JdbcTemplate jdbc, long numeric) {
        long current = jdbc.queryForObject("SELECT recovery_epoch FROM server_state", Long.class);
        if (current == numeric) return;
        jdbc.update("UPDATE server_state SET recovery_epoch=? WHERE id=1", numeric);
    }

    /** Without an external intent the admission may proceed, but the record must say identity is not evidence. */
    private static void withoutDurableIntent(JdbcTemplate jdbc, RecoveryTransactionFixture fixture,
            long baseline, Path evidence) throws Exception {
        assertThat(register(jdbc, fixture, baseline).outcome()).isEqualTo("REGISTERED");
        assertThat(register(jdbc, fixture, baseline).outcome()).isEqualTo("ALREADY_REGISTERED");
        assertThat(mode(jdbc)).isEqualTo("NORMAL");
        assertThat(jdbc.queryForObject("SELECT operator_id FROM recovery_normal_admission WHERE id=1", String.class))
            .isEqualTo("UNRECORDED_EXTERNAL_INTENT");
        Files.writeString(evidence.resolve("normal-without-intent.json"), JSON.writeValueAsString(register(jdbc, fixture, baseline)));
    }

    private static void withDurableIntent(JdbcTemplate jdbc, RecoveryTransactionFixture fixture, long baseline,
            Path evidence) throws Exception {
        // the previous case left NORMAL with no row; a matching intent may register from that closed state
        reopen(jdbc, baseline + 1);
        writeIntent(jdbc, fixture, baseline, null, null, "operator-normal-1", null);
        assertThat(register(jdbc, fixture, baseline).outcome()).isEqualTo("REGISTERED");
        // start the durable-intent cases from the closed state again: drop the row and re-open PENDING
        reopen(jdbc, baseline + 1);
        var fresh = register(jdbc, fixture, baseline);        assertThat(fresh.outcome()).isEqualTo("REGISTERED");
        assertThat(fresh.operatorId()).isEqualTo("operator-normal-1");
        assertThat(fresh.ordinaryWritesAllowed()).isFalse();
        assertThat(mode(jdbc)).isEqualTo("NORMAL");
        assertThat(count(jdbc, "recovery_normal_admission")).isEqualTo(1);
        assertThat(count(jdbc, "recovery_control_projection")).isEqualTo(4);
        // a replay must not write anything new and must keep the same registered facts
        var replay = register(jdbc, fixture, baseline);
        assertThat(replay.outcome()).isEqualTo("ALREADY_REGISTERED");
        assertThat(replay.installationId()).isEqualTo(fresh.installationId());
        assertThat(replay.recordSha256()).isEqualTo(fresh.recordSha256());
        assertThat(replay.numericEpoch()).isEqualTo(fresh.numericEpoch());
        assertThat(replay.operatorId()).isEqualTo(fresh.operatorId());
        assertThat(count(jdbc, "recovery_normal_admission")).isEqualTo(1);

        // the fresh registration above left NORMAL; every rejection case must start closed again
        reopen(jdbc, baseline + 1);
        writeIntent(jdbc, fixture, baseline, null, "other_schema", "operator-normal-1", null);
        registerFails(jdbc, fixture, baseline, "CONTROL_NORMAL_INTENT_SCHEMA_MISMATCH");
        // a forged numeric baseline must be rejected before any write
        reopen(jdbc, baseline + 1);
        writeIntent(jdbc, fixture, baseline, baseline + 7L, null, "operator-normal-1", null);
        registerFails(jdbc, fixture, baseline, "CONTROL_NORMAL_INTENT_BASELINE_MISMATCH");
        openForIntent(jdbc, fixture, baseline, null, "operator-normal-1", RecoveryNormalChecks::flipFirst);
        registerFails(jdbc, fixture, baseline, "CONTROL_NORMAL_INTENT_CONTROL_MISMATCH");
        reopen(jdbc, baseline + 1);
        Files.delete(fixture.location.root().resolve(RecoveryNormalIntent.FILE));
        // no durable intent at all: the closed state may still be admitted, and the row must say so
        assertThat(register(jdbc, fixture, baseline).outcome()).isEqualTo("REGISTERED");
        assertThat(jdbc.queryForObject("SELECT operator_id FROM recovery_normal_admission WHERE id=1", String.class))
            .isEqualTo("UNRECORDED_EXTERNAL_INTENT");
        reopen(jdbc, baseline + 1);

        Files.deleteIfExists(fixture.location.root().resolve(RecoveryNormalIntent.FILE));
        assertThat(register(jdbc, fixture, baseline).outcome()).isEqualTo("REGISTERED");
        // a replay that declares a different durable intent must be refused against the existing row
        writeIntent(jdbc, fixture, baseline, null, null, "operator-normal-2", null);
        assertThatThrownBy(() -> register(jdbc, fixture, baseline))
            .isInstanceOf(RecoveryControlException.class).hasMessage("CONTROL_NORMAL_INTENT_CONFLICT");
        assertThat(count(jdbc, "recovery_normal_admission")).isEqualTo(1);
        assertThat(mode(jdbc)).isEqualTo("NORMAL");
        reopen(jdbc, baseline + 1);
        writeIntent(jdbc, fixture, baseline, null, null, "operator-normal-1", null);
        Files.writeString(evidence.resolve("normal-admission.json"), JSON.writeValueAsString(register(jdbc, fixture, baseline)));

        assertThat(mode(jdbc)).isEqualTo("NORMAL");
        for (String table : java.util.List.of("recovery_normal_admission", "recovery_control_projection")) {
            assertThatThrownBy(() -> jdbc.update("UPDATE " + table + " SET installation_id=installation_id"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("DELETE FROM " + table))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
    }

    /** Puts the database back in RECOVERY_PENDING so the next case starts from the unsanctioned state. */
    private static void requirePending(JdbcTemplate jdbc, RecoveryTransactionFixture fixture, long baseline, String code) {
        jdbc.update("UPDATE server_state SET service_mode='RECOVERY_PENDING' WHERE id=1");
        resetAdmission(jdbc);
        registerFails(jdbc, fixture, baseline, code);
    }

    private static String flipFirst(String hash) {
        return (hash.charAt(0) == 'a' ? 'b' : 'a') + hash.substring(1);
    }

    /** Forces the closed state the registration contract expects: PENDING mode, projected numeric epoch. */
    private static void reopen(JdbcTemplate jdbc, long projectedNumeric) {
        resetAdmission(jdbc);
        jdbc.update("UPDATE server_state SET service_mode='RECOVERY_PENDING',recovery_epoch=? WHERE id=1", projectedNumeric);
    }

    /** Closed state plus a specific durable intent, so each rejection case is independent. */
    private static void openForIntent(JdbcTemplate jdbc, RecoveryTransactionFixture fixture, long baseline, Long claimed,
            String operator, HashEdit edit) throws Exception {
        reopen(jdbc, baseline + 1);
        writeIntent(jdbc, fixture, baseline, claimed, null, operator, edit);
    }

    private static RecoveryNormalRegistration.Result register(JdbcTemplate jdbc, RecoveryTransactionFixture fixture, long baseline) {
        return new RecoveryNormalRegistration(jdbc.getDataSource(), fixture.location, fixture.checkpoint, baseline).register();
    }

    private static void registerFails(JdbcTemplate jdbc, RecoveryTransactionFixture fixture, long baseline, String code) {
        assertThatThrownBy(() -> register(jdbc, fixture, baseline)).isInstanceOf(RecoveryControlException.class).hasMessage(code);
        assertThat(mode(jdbc)).isNotEqualTo("NORMAL");
        assertThat(count(jdbc, "recovery_normal_admission")).isZero();
    }

    private static String mode(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT service_mode FROM server_state WHERE id=1", String.class);
    }

    private static void resetAdmission(JdbcTemplate jdbc) {
        jdbc.execute("DROP TRIGGER IF EXISTS recovery_normal_no_delete");
        jdbc.update("DELETE FROM recovery_normal_admission");
        jdbc.execute("CREATE TRIGGER recovery_normal_no_delete BEFORE DELETE ON recovery_normal_admission FOR EACH ROW "
            + "BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Recovery normal admission is retained'; END");
    }

    private static ObjectNode head(RecoveryTransactionFixture fixture) throws Exception {
        Path directory = fixture.location.root().resolve("history");
        Path last;
        try (var entries = Files.list(directory)) { last = entries.sorted().toList().getLast(); }
        return (ObjectNode) JSON.readTree(Files.readAllBytes(last));
    }

    private interface HashEdit { String apply(String hash); }

    private static void writeIntent(JdbcTemplate jdbc, RecoveryTransactionFixture fixture, long baseline, Long claimedBaseline,
            String schema, String operator, HashEdit edit) throws Exception {
        ObjectNode record = head(fixture);
        String recordHash = record.path("recordSha256").asText();
        String schemaName = jdbc.queryForObject("SELECT DATABASE()", String.class);
        ObjectNode intent = JSON.createObjectNode().put("formatVersion", 1)
            .put("installationId", fixture.location.installation().toString())
            .put("expectedDatabaseSchema", schema == null ? schemaName : schema)
            .put("expectedBaselineNumericEpoch", claimedBaseline == null ? baseline : claimedBaseline)
            .put("controlRevision", record.path("revision").asLong())
            .put("controlRecordSha256", edit == null ? recordHash : edit.apply(recordHash))
            .put("previousRecordSha256", record.path("previousRecordSha256").asText())
            .put("operatorId", operator).put("requestedAt", "2026-09-26T00:00:00Z");
        intent.put("intentSha256", RecoveryControlFormat.hash(intent));
        Files.writeString(fixture.location.root().resolve(RecoveryNormalIntent.FILE), RecoveryControlFormat.canonical(intent));
    }

    private static int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
