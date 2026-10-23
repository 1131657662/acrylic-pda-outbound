package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Append-only successor facts. Callers hold the closed lease and server_state row lock. */
final class RecoverySuccessorRows {
    private final JdbcTemplate jdbc;
    RecoverySuccessorRows(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    List<ObjectNode> bindings(UUID installation) {
        return jdbc.query("SELECT start_control_revision,start_control_sha256,predecessor_release_sha256,"
            + "predecessor_intent_sha256,source_binding_sha256,source_normal_sha256,BIN_TO_UUID(mysql_server_uuid),"
            + "database_schema,binding_sha256 FROM recovery_successor_binding WHERE installation_id=? "
            + "ORDER BY start_control_revision LIMIT 100001", (rs, index) -> base(installation)
                .put("startRevision", rs.getLong(1)).put("startHash", hex(rs.getBytes(2)))
                .put("predecessorRelease", hex(rs.getBytes(3))).put("predecessorIntent", hex(rs.getBytes(4)))
                .put("sourceBinding", hex(rs.getBytes(5))).put("sourceNormal", hex(rs.getBytes(6)))
                .put("serverUuid", rs.getString(7)).put("schema", rs.getString(8))
                .put("bindingHash", hex(rs.getBytes(9))), Ids.bytes(installation));
    }

    List<ObjectNode> normals(UUID installation) {
        return jdbc.query("SELECT binding_revision,control_revision,control_record_sha256,previous_record_sha256,"
            + "intent_sha256,numeric_epoch,operator_id,normal_sha256 FROM recovery_successor_normal "
            + "WHERE installation_id=? ORDER BY control_revision LIMIT 100001", (rs, index) -> base(installation)
                .put("bindingRevision", rs.getLong(1)).put("revision", rs.getLong(2))
                .put("recordHash", hex(rs.getBytes(3))).put("previousHash", hex(rs.getBytes(4)))
                .put("intentHash", hex(rs.getBytes(5))).put("numeric", rs.getLong(6))
                .put("operator", rs.getString(7)).put("normalHash", hex(rs.getBytes(8))), Ids.bytes(installation));
    }

    void bind(ObjectNode value) {
        jdbc.update("INSERT INTO recovery_successor_binding(installation_id,start_control_revision,start_control_sha256,"
            + "predecessor_release_sha256,predecessor_intent_sha256,source_binding_sha256,source_normal_sha256,"
            + "mysql_server_uuid,database_schema,binding_sha256) VALUES(?,?,?,?,?,?,?,?,?,?)",
            Ids.bytes(uuid(value, "installationId")), value.path("startRevision").longValue(), bytes(text(value, "startHash")),
            bytes(text(value, "predecessorRelease")), bytes(text(value, "predecessorIntent")), bytes(text(value, "sourceBinding")),
            bytes(text(value, "sourceNormal")), Ids.bytes(uuid(value, "serverUuid")), text(value, "schema"), bytes(text(value, "bindingHash")));
    }

    void admit(ObjectNode value) {
        jdbc.update("INSERT INTO recovery_successor_normal(installation_id,binding_revision,control_revision,"
            + "control_record_sha256,previous_record_sha256,intent_sha256,numeric_epoch,operator_id,normal_sha256) "
            + "VALUES(?,?,?,?,?,?,?,?,?)", Ids.bytes(uuid(value, "installationId")), value.path("bindingRevision").longValue(),
            value.path("revision").longValue(), bytes(text(value, "recordHash")), bytes(text(value, "previousHash")),
            bytes(text(value, "intentHash")), value.path("numeric").longValue(), text(value, "operator"), bytes(text(value, "normalHash")));
    }

    static ObjectNode binding(com.fasterxml.jackson.databind.JsonNode record) {
        var command = record.path("command");
        var value = base(uuid(record, "installationId")).put("startRevision", revision(record, "revision"))
            .put("startHash", text(record, "recordSha256")).put("predecessorRelease", text(command, "predecessorReleaseSha256"))
            .put("predecessorIntent", text(command, "predecessorNormalIntentSha256"))
            .put("sourceBinding", text(command, "sourceBindingSha256")).put("sourceNormal", text(command, "sourceNormalSha256"))
            .put("serverUuid", text(command, "targetServerUuid")).put("schema", text(command, "targetDatabaseSchema"));
        return value.put("bindingHash", RecoveryDatabaseHashes.body(value, "bindingHash"));
    }

    static ObjectNode normal(ObjectNode binding, RecoveryNormalIntent.Intent intent, long numeric) {
        var value = base(intent.installation()).put("bindingRevision", binding.path("startRevision").longValue())
            .put("revision", intent.revision()).put("recordHash", intent.recordSha256()).put("previousHash", intent.previousRecordSha256())
            .put("intentHash", intent.intentSha256()).put("numeric", numeric).put("operator", intent.operatorId());
        return value.put("normalHash", RecoveryDatabaseHashes.body(value, "normalHash"));
    }

    private static ObjectNode base(UUID installation) {
        return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
            .put("formatVersion", 1).put("installationId", installation.toString());
    }
}
