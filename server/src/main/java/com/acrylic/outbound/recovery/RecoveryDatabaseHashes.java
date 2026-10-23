package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Canonical logical-row digests, including every original column and its registration timestamp. */
final class RecoveryDatabaseHashes {
    private RecoveryDatabaseHashes() { }

    static String binding(JdbcTemplate jdbc) {
        return original(jdbc, "SELECT id,BIN_TO_UUID(installation_id) AS installation_id,"
            + "BIN_TO_UUID(mysql_server_uuid) AS mysql_server_uuid,database_schema,baseline_numeric_epoch,"
            + "initial_control_sha256,DATE_FORMAT(registered_at,'%Y-%m-%dT%H:%i:%s.%f') AS registered_at "
            + "FROM recovery_installation_binding WHERE id=1", "recovery_installation_binding");
    }

    static String normal(JdbcTemplate jdbc) {
        return original(jdbc, "SELECT id,format_version,BIN_TO_UUID(installation_id) AS installation_id,"
            + "authority_revision,control_revision,control_record_sha256,previous_record_sha256,intent_sha256,"
            + "numeric_epoch,operator_id,DATE_FORMAT(registered_at,'%Y-%m-%dT%H:%i:%s.%f') AS registered_at "
            + "FROM recovery_normal_admission WHERE id=1", "recovery_normal_admission");
    }

    private static String original(JdbcTemplate jdbc, String sql, String table) {
        var rows = jdbc.query(sql, (rs, row) -> {
            var value = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            value.put("formatVersion", 1).put("table", table);
            for (int column = 1; column <= rs.getMetaData().getColumnCount(); column++) {
                String name = rs.getMetaData().getColumnLabel(column); Object cell = rs.getObject(column);
                require(cell != null, "CONTROL_SUCCESSOR_SOURCE_ROW_INCOMPLETE");
                if (cell instanceof byte[] bytes) value.put(name, HexFormat.of().formatHex(bytes));
                else if (cell instanceof Number number) value.put(name, number.longValue());
                else value.put(name, cell.toString());
            }
            return hash(value);
        });
        require(rows.size() == 1, "CONTROL_SUCCESSOR_SOURCE_ROW_REQUIRED");
        return rows.getFirst();
    }

    static String body(ObjectNode value, String digestField) {
        ObjectNode body = value.deepCopy(); body.remove(digestField); return hash(body);
    }
}
