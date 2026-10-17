package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Allows exactly one reader login while conserving all other captured facts. */
final class E2aProgramReadFacts {
    static ObjectNode capture(E2aUpgradeCheckpoint checkpoint, JdbcTemplate jdbc, Path storage) throws Exception {
        var facts = checkpoint.capture(jdbc, storage);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        facts.set("triggers", json.readTree(json.writeValueAsBytes(jdbc.queryForList(
            "SELECT TRIGGER_NAME,EVENT_MANIPULATION,EVENT_OBJECT_TABLE,ACTION_ORDER,ACTION_CONDITION,"
            + "ACTION_STATEMENT,ACTION_ORIENTATION,ACTION_TIMING,SQL_MODE,DEFINER,CHARACTER_SET_CLIENT,"
            + "COLLATION_CONNECTION,DATABASE_COLLATION FROM information_schema.TRIGGERS "
            + "WHERE TRIGGER_SCHEMA=DATABASE() ORDER BY TRIGGER_NAME"))));
        return facts;
    }

    static void verify(ObjectNode before, ObjectNode after, E2aUpgradeRollbackProgram.Reader reader) {
        assertThat(names(after)).isEqualTo(names(before));
        assertThat(after.path("files")).isEqualTo(before.path("files"));
        assertThat(after.path("flywayHistory")).isEqualTo(before.path("flywayHistory"));
        assertThat(after.path("triggers")).isEqualTo(before.path("triggers"));
        assertThat(schema(after)).isEqualTo(schema(before));
        assertThat(counter(after)).isEqualTo(counter(before) + 1);
        var oldTables = before.path("tables"); var newTables = after.path("tables");
        assertThat(names(newTables)).isEqualTo(names(oldTables));
        oldTables.fields().forEachRemaining(entry -> {
            if (!Set.of("operation_event", "spring_session", "spring_session_attributes").contains(entry.getKey()))
                assertThat(newTables.path(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());
        });
        audit(oldTables.path("operation_event"), newTables.path("operation_event"), reader);
        var session = addition(oldTables.path("spring_session"), newTables.path("spring_session"));
        assertThat(text(newTables.path("spring_session"), session, "PRINCIPAL_NAME")).isEqualTo(reader.username());
        var attributes = newTables.path("spring_session_attributes");
        var attribute = addition(oldTables.path("spring_session_attributes"), attributes);
        assertThat(text(attributes, attribute, "SESSION_PRIMARY_ID"))
            .isEqualTo(text(newTables.path("spring_session"), session, "PRIMARY_ID"));
        assertThat(text(attributes, attribute, "ATTRIBUTE_NAME")).isEqualTo("SPRING_SECURITY_CONTEXT");
    }

    private static void audit(JsonNode before, JsonNode after, E2aUpgradeRollbackProgram.Reader reader) {
        var row = addition(before, after);
        String actor = Base64.getEncoder().encodeToString(com.acrylic.outbound.common.Ids.bytes(reader.id()));
        assertThat(cell(after, row, "actor_id").asText()).isEqualTo(actor);
        assertThat(cell(after, row, "entity_id").asText()).isEqualTo(actor);
        assertThat(text(after, row, "event_type")).isEqualTo("WEB_LOGIN");
        assertThat(text(after, row, "entity_type")).isEqualTo("USER");
        assertThat(text(after, row, "details")).isEqualTo("{}");
    }

    private static JsonNode addition(JsonNode before, JsonNode after) {
        assertThat(after.path("columns")).isEqualTo(before.path("columns"));
        var remaining = list(after.path("rows"));
        for (var row : before.path("rows")) assertThat(remaining.remove(row)).as("Original row conserved").isTrue();
        assertThat(remaining).as("Exactly one authentication row added").hasSize(1);
        return remaining.getFirst();
    }

    private static ObjectNode schema(ObjectNode snapshot) {
        var result = (ObjectNode) snapshot.path("schema").deepCopy();
        result.put("operation_event", result.path("operation_event").asText().replaceAll(" AUTO_INCREMENT=\\d+", ""));
        return result;
    }

    private static long counter(ObjectNode snapshot) {
        var matcher = java.util.regex.Pattern.compile(" AUTO_INCREMENT=(\\d+)")
            .matcher(snapshot.path("schema").path("operation_event").asText());
        return matcher.find() ? Long.parseLong(matcher.group(1)) : 1;
    }

    private static JsonNode cell(JsonNode table, JsonNode row, String column) {
        int index = list(table.path("columns")).indexOf(com.fasterxml.jackson.databind.node.TextNode.valueOf(column));
        assertThat(index).isNotNegative(); return row.get(index);
    }

    private static String text(JsonNode table, JsonNode row, String column) {
        return new String(Base64.getDecoder().decode(cell(table, row, column).asText()), StandardCharsets.UTF_8);
    }

    private static List<JsonNode> list(JsonNode node) {
        var result = new ArrayList<JsonNode>(); node.forEach(result::add); return result;
    }

    private static Set<String> names(JsonNode node) {
        var result = new TreeSet<String>(); node.fieldNames().forEachRemaining(result::add); return result;
    }

    private E2aProgramReadFacts() { }
}
