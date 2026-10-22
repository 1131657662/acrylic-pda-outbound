package com.acrylic.outbound.recovery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryAdmissionEvidence.*;

/** A single read statement binds the physical database identity to its closed admission rows. */
final class RecoveryClosedAdmissionQueries {
    private RecoveryClosedAdmissionQueries() { }

    static Closed read(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
            SELECT @@server_uuid AS actual_server,DATABASE() AS actual_schema,
                BIN_TO_UUID(b.installation_id) AS installation,BIN_TO_UUID(b.mysql_server_uuid) AS bound_server,
                b.database_schema,b.baseline_numeric_epoch,a.authority_revision,a.control_record_sha256,a.intent_sha256,
                p.control_revision,p.record_sha256,p.control_phase,p.numeric_epoch,s.service_mode,s.recovery_epoch
            FROM server_state s
            LEFT JOIN recovery_installation_binding b ON b.id=1
            LEFT JOIN recovery_normal_admission a ON a.id=1
            LEFT JOIN recovery_control_projection p ON p.installation_id=b.installation_id
                AND p.control_revision=(SELECT MAX(control_revision) FROM recovery_control_projection
                    WHERE installation_id=b.installation_id)
            WHERE s.id=1
            """, (rs, row) -> snapshot(rs));
    }

    private static Closed snapshot(ResultSet rs) throws SQLException {
        var binding = rs.getString("installation") == null ? null : new Binding(
            UUID.fromString(rs.getString("installation")), rs.getLong("baseline_numeric_epoch"));
        var registered = binding == null ? null : new Database(
            UUID.fromString(rs.getString("bound_server")), rs.getString("database_schema"));
        var admission = rs.getObject("authority_revision") == null ? null : new Admission(
            rs.getLong("authority_revision"), hex(rs, "control_record_sha256"), hex(rs, "intent_sha256"));
        var projection = rs.getObject("control_revision") == null ? null : new Projection(
            rs.getLong("control_revision"), hex(rs, "record_sha256"), rs.getString("control_phase"), rs.getLong("numeric_epoch"));
        return new Closed(new Database(UUID.fromString(rs.getString("actual_server")), rs.getString("actual_schema")),
            registered, binding, admission, projection, new State(rs.getString("service_mode"), rs.getLong("recovery_epoch")));
    }

    private static String hex(ResultSet rs, String column) throws SQLException {
        byte[] value = rs.getBytes(column);
        return value == null ? null : HexFormat.of().formatHex(value);
    }
}
