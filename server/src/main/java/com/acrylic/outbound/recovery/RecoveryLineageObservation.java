package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import com.acrylic.outbound.recovery.RecoveryProjectionRows.*;

/** Decodes a single SQL observation into the exact same logical facts used by closed commands. */
final class RecoveryLineageObservation {
    private RecoveryLineageObservation() { }

    static RecoveryLineageFacts facts(JsonNode value) {
        var binding = value.path("binding"); var initial = value.path("initialNormal");
        require(binding.isObject() && initial.isObject(), "CONTROL_SUCCESSOR_NORMAL_REQUIRED");
        var installation = uuid(binding, "installation_id");
        require(uuid(initial, "installation_id").equals(installation) && number(initial, "format_version") == 1
            && number(initial, "id") == 1 && number(binding, "id") == 1, "CONTROL_SUCCESSOR_NORMAL_CONFLICT");
        var original = new Binding(installation, text(binding, "mysql_server_uuid"), text(binding, "database_schema"),
            number(binding, "baseline_numeric_epoch"), text(binding, "initial_control_sha256"));
        var normal = new RecoveryNormalRows.Record(number(initial, "control_revision"), text(initial, "control_record_sha256"),
            text(initial, "previous_record_sha256"), text(initial, "intent_sha256"), number(initial, "numeric_epoch"),
            text(initial, "operator_id"), number(initial, "authority_revision"));
        return new RecoveryLineageFacts(original, projections(value, installation), epochs(value, installation), normal,
            originalHash(binding, "recovery_installation_binding"), originalHash(initial, "recovery_normal_admission"),
            array(value, "bindings", "startRevision"), array(value, "normals", "revision"));
    }

    private static List<Projection> projections(JsonNode value, UUID installation) {
        var result = new ArrayList<Projection>();
        for (var row : array(value, "projections", "revision")) {
            require(uuid(row, "installation").equals(installation), "CONTROL_DATABASE_INSTALLATION_MISMATCH");
            result.add(new Projection(number(row, "revision"), text(row, "hash"), text(row, "previous"),
                uuid(row, "epoch"), number(row, "numeric"), text(row, "phase")));
        }
        return result;
    }

    private static List<Epoch> epochs(JsonNode value, UUID installation) {
        var result = new ArrayList<Epoch>();
        for (var row : array(value, "epochs", "revision")) {
            require(uuid(row, "installation").equals(installation), "CONTROL_DATABASE_INSTALLATION_MISMATCH");
            result.add(new Epoch(uuid(row, "epoch"), number(row, "numeric"), number(row, "revision"), text(row, "hash")));
        }
        return result;
    }

    static List<ObjectNode> array(JsonNode value, String field, String order) {
        var array = value.path(field); var result = new ArrayList<ObjectNode>();
        if (array.isNull()) return result;
        require(array.isArray() && array.size() <= 100000, "CONTROL_DATABASE_CHAIN_CONFLICT");
        for (var row : array) {
            require(row.isObject(), "CONTROL_DATABASE_CHAIN_CONFLICT");
            number(row, order); var copy = ((ObjectNode) row).deepCopy();
            row.properties().forEach(cell -> {
                if (cell.getValue().isIntegralNumber() && !cell.getKey().equals("formatVersion")) {
                    require(cell.getValue().canConvertToLong(), "CONTROL_DATABASE_CHAIN_CONFLICT");
                    copy.put(cell.getKey(), cell.getValue().longValue());
                }
            });
            result.add(copy);
        }
        result.sort(Comparator.comparingLong(row -> number(row, order))); return result;
    }

    static long number(JsonNode value, String field) {
        var cell = value.path(field);
        require(cell.isIntegralNumber() && cell.canConvertToLong() && cell.longValue() > 0, "CONTROL_DATABASE_CHAIN_CONFLICT");
        return cell.longValue();
    }

    private static String originalHash(JsonNode row, String table) {
        var body = ((ObjectNode) row).deepCopy();
        body.put("formatVersion", 1).put("table", table);
        for (var cell : row) require(!cell.isNull(), "CONTROL_SUCCESSOR_SOURCE_ROW_INCOMPLETE");
        return hash(body);
    }
}
