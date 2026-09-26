package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** NORMAL admission records. The caller already holds the external lease and the server_state lock. */
final class RecoveryNormalRows {
    private final JdbcTemplate jdbc;
    RecoveryNormalRows(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Record existing() {
        var rows = jdbc.query("SELECT control_revision,control_record_sha256,previous_record_sha256,intent_sha256,numeric_epoch,operator_id,authority_revision "
            + "FROM recovery_normal_admission WHERE id=1",
            (rs, row) -> new Record(rs.getLong(1), hex(rs.getBytes(2)), hex(rs.getBytes(3)), hex(rs.getBytes(4)),
                rs.getLong(5), rs.getString(6), rs.getLong(7)));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    void admit(UUID installation, long authorityRevision, long revision, String recordHash, String previousHash,
            String intentHash, long numeric, String operator) {
        var parameters = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
            .addValue("installation", Ids.bytes(installation), java.sql.Types.BINARY)
            .addValue("authorityRevision", authorityRevision)
            .addValue("revision", revision)
            .addValue("record", hash(recordHash), java.sql.Types.BINARY)
            .addValue("previous", hash(previousHash), java.sql.Types.BINARY)
            .addValue("intent", hash(intentHash), java.sql.Types.BINARY)
            .addValue("numeric", numeric)
            .addValue("operator", operator);
        new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).update("""
            INSERT INTO recovery_normal_admission(id,format_version,installation_id,authority_revision,control_revision,
                control_record_sha256,previous_record_sha256,intent_sha256,numeric_epoch,operator_id)
            VALUES(1,1,:installation,:authorityRevision,:revision,:record,:previous,:intent,:numeric,:operator)
            """, parameters);
    }

    /** Registered rows are immutable and unique, so this is the only state change NORMAL admission makes. */
    void enableNormal() {
        jdbc.update("UPDATE server_state SET service_mode='NORMAL' WHERE id=1");
    }

    private static byte[] hash(String value) { return HexFormat.of().parseHex(value); }
    private static String hex(byte[] value) { return HexFormat.of().formatHex(value); }

    record Record(long revision, String hash, String previousHash, String intentHash, long numeric, String operator,
        long authorityRevision) { }
}
