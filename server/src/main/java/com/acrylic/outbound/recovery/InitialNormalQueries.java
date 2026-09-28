package com.acrylic.outbound.recovery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** One statement observes mode, actual DB identity, closed binding and the complete initial row. */
final class InitialNormalQueries {
    private InitialNormalQueries() { }

    static RecoveryAdmissionEvidence.Initial read(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
            SELECT @@server_uuid AS actual_server,DATABASE() AS actual_schema,s.service_mode,s.recovery_epoch,
                EXISTS(SELECT 1 FROM recovery_installation_binding) AS closed_history,
                r.format_version,BIN_TO_UUID(r.installation_id) AS installation,BIN_TO_UUID(r.epoch_id) AS epoch,
                r.numeric_epoch,BIN_TO_UUID(r.mysql_server_uuid) AS server_uuid,r.database_schema,
                BIN_TO_UUID(r.command_id) AS command_id,r.request_sha256,r.request_bytes
            FROM server_state s LEFT JOIN recovery_initial_normal r ON r.id=1 WHERE s.id=1
            """, (rs, row) -> new RecoveryAdmissionEvidence.Initial(UUID.fromString(rs.getString("actual_server")),
                rs.getString("actual_schema"), new RecoveryAdmissionEvidence.State(rs.getString("service_mode"),
                rs.getLong("recovery_epoch")), rs.getBoolean("closed_history"), original(rs)));
    }

    private static InitialNormalEvidence original(ResultSet rs) throws SQLException {
        byte[] bytes = rs.getBytes("request_bytes");
        if (bytes == null) return null;
        var original = InitialNormalEvidence.parse(bytes);
        require(rs.getInt("format_version") == 1
            && original.installation().toString().equals(rs.getString("installation"))
            && original.epoch().toString().equals(rs.getString("epoch"))
            && original.numericEpoch() == rs.getLong("numeric_epoch")
            && original.serverUuid().toString().equals(rs.getString("server_uuid"))
            && original.schema().equals(rs.getString("database_schema"))
            && original.command().toString().equals(rs.getString("command_id"))
            && original.sha256().equals(HexFormat.of().formatHex(rs.getBytes("request_sha256"))),
            "INITIAL_NORMAL_DATABASE_ROW_CONFLICT");
        return original;
    }
}
