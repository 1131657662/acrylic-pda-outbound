package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** SQL projection records. All callers already hold the external lease and server_state lock. */
final class RecoveryProjectionRows {
    private final JdbcTemplate jdbc;
    RecoveryProjectionRows(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    State lockState() {
        return jdbc.queryForObject("SELECT recovery_epoch,service_mode FROM server_state WHERE id=1 FOR UPDATE",
            (rs, row) -> new State(rs.getLong(1), rs.getString(2)));
    }

    Binding binding() {
        var rows = jdbc.query("SELECT BIN_TO_UUID(installation_id),BIN_TO_UUID(mysql_server_uuid),database_schema,baseline_numeric_epoch,initial_control_sha256 "
            + "FROM recovery_installation_binding WHERE id=1",
            (rs, row) -> new Binding(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3), rs.getLong(4),
                HexFormat.of().formatHex(rs.getBytes(5))));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    Database database() {
        return jdbc.queryForObject("SELECT @@server_uuid,DATABASE()", (rs, row) -> new Database(rs.getString(1), rs.getString(2)));
    }

    List<Projection> history(UUID installation) {
        return jdbc.query("""
            SELECT control_revision,record_sha256,previous_record_sha256,BIN_TO_UUID(epoch_id),numeric_epoch,control_phase
            FROM recovery_control_projection WHERE installation_id=? ORDER BY control_revision LIMIT 100001
            """, (rs, row) -> new Projection(rs.getLong(1), HexFormat.of().formatHex(rs.getBytes(2)),
                HexFormat.of().formatHex(rs.getBytes(3)), UUID.fromString(rs.getString(4)), rs.getLong(5), rs.getString(6)), Ids.bytes(installation));
    }

    List<Epoch> epochs(UUID installation) {
        return jdbc.query("SELECT BIN_TO_UUID(epoch_id),numeric_epoch,first_control_revision,first_control_sha256 "
            + "FROM recovery_control_epoch WHERE installation_id=? ORDER BY first_control_revision LIMIT 100001",
            (rs, row) -> new Epoch(UUID.fromString(rs.getString(1)), rs.getLong(2), rs.getLong(3),
                HexFormat.of().formatHex(rs.getBytes(4))), Ids.bytes(installation));
    }

    void register(RecoveryControlSnapshot snapshot, long numeric, Database database) {
        jdbc.update("INSERT INTO recovery_installation_binding(id,installation_id,mysql_server_uuid,database_schema,baseline_numeric_epoch,initial_control_sha256) "
            + "VALUES(1,?,?,?,?,?)", Ids.bytes(snapshot.installationId()), Ids.bytes(UUID.fromString(database.serverUuid())),
            database.schema(), numeric, hash(snapshot.recordSha256()));
    }

    void map(RecoveryControlSnapshot snapshot, long numeric) {
        jdbc.update("INSERT INTO recovery_control_epoch(installation_id,epoch_id,numeric_epoch,first_control_revision,first_control_sha256) VALUES(?,?,?,?,?)",
            Ids.bytes(snapshot.installationId()), Ids.bytes(snapshot.epochId()), numeric, Long.parseLong(snapshot.revision()), hash(snapshot.recordSha256()));
    }

    void append(RecoveryControlSnapshot snapshot, long numeric) {
        jdbc.update("""
            INSERT INTO recovery_control_projection(installation_id,control_revision,record_sha256,previous_record_sha256,
                epoch_id,numeric_epoch,control_phase) VALUES(?,?,?,?,?,?,?)
            """, Ids.bytes(snapshot.installationId()), Long.parseLong(snapshot.revision()), hash(snapshot.recordSha256()),
            hash(snapshot.previousRecordSha256()), Ids.bytes(snapshot.epochId()), numeric, snapshot.phase());
        // The row is already locked and known to exist; affected-row semantics may report zero
        // when VERIFYING/RELEASE_READY keep the same numeric epoch and closed service mode.
        jdbc.update("UPDATE server_state SET recovery_epoch=?,service_mode='RECOVERY_PENDING' WHERE id=1", numeric);
    }

    private static byte[] hash(String value) { return HexFormat.of().parseHex(value); }
    record State(long numeric, String mode) { }
    record Database(String serverUuid, String schema) { }
    record Binding(UUID installation, String serverUuid, String schema, long baseline, String initialHash) { }
    record Epoch(UUID epoch, long numeric, long revision, String hash) { }
    record Projection(long revision, String hash, String previousHash, UUID epoch, long numeric, String phase) { }
}
