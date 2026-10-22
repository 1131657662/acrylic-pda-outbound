package com.acrylic.outbound.recovery;

import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Read-only SQL evidence for the admission verifier. No statement here writes. */
public final class RecoveryAdmissionQueries implements RecoveryAdmissionEvidence {
    private final JdbcTemplate jdbc;

    public RecoveryAdmissionQueries(DataSource source) { this.jdbc = new JdbcTemplate(source); }

    @Override public Initial initial() { return InitialNormalQueries.read(jdbc); }

    @Override public Closed closed() { return RecoveryClosedAdmissionQueries.read(jdbc); }

    @Override public Binding binding() {
        var rows = jdbc.query("SELECT BIN_TO_UUID(installation_id),baseline_numeric_epoch FROM recovery_installation_binding WHERE id=1",
            (rs, row) -> new Binding(UUID.fromString(rs.getString(1)), rs.getLong(2)));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Override public Admission admission() {
        var rows = jdbc.query("SELECT authority_revision,control_record_sha256,intent_sha256 FROM recovery_normal_admission WHERE id=1",
            (rs, row) -> new Admission(rs.getLong(1), hex(rs.getBytes(2)), hex(rs.getBytes(3))));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Override public Projection projection() {
        List<Projection> rows = jdbc.query("""
            SELECT control_revision,record_sha256,control_phase,numeric_epoch FROM recovery_control_projection
            WHERE installation_id=(SELECT installation_id FROM recovery_installation_binding WHERE id=1)
            ORDER BY control_revision DESC LIMIT 1
            """, (rs, row) -> new Projection(rs.getLong(1), hex(rs.getBytes(2)), rs.getString(3), rs.getLong(4)));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Override public State state() {
        return jdbc.queryForObject("SELECT service_mode,recovery_epoch FROM server_state WHERE id=1",
            (rs, row) -> new State(rs.getString(1), rs.getLong(2)));
    }

    static UUID installation(String text) { return UUID.fromString(text); }

    private static String hex(byte[] value) { return HexFormat.of().formatHex(value); }
}
