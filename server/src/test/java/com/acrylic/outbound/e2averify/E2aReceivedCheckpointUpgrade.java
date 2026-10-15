package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Upgrade the verified historical checkpoint before running candidate-only inventory queries. */
final class E2aReceivedCheckpointUpgrade {
    static void verify(E2aCrossSourceProfile profile, JdbcTemplate jdbc) throws Exception {
        var snapshots = new E2aMigrationSnapshot(jdbc, profile.attempt, profile.attempt.resolve("storage"));
        var before = snapshots.capture();
        Files.writeString(profile.attempt.resolve("history-upgrade-before.json"), before.toPrettyString());
        profile.migrate(jdbc);
        var after = snapshots.capture();
        Files.writeString(profile.attempt.resolve("history-upgrade-after.json"), after.toPrettyString());
        legacyTables(before.path("tables"), after.path("tables"));
        permissionAdditions(before.path("tables"), after.path("tables"));
        captureRevisions(after.path("tables"));
        assertThat(after.path("files")).isEqualTo(before.path("files"));
        assertThat(after.path("database")).isEqualTo(before.path("database"));
        var oldHistory = before.path("flywayHistory"); var newHistory = after.path("flywayHistory");
        assertThat(newHistory.size()).isGreaterThan(oldHistory.size());
        for (int index = 0; index < oldHistory.size(); index++)
            assertThat(newHistory.get(index)).isEqualTo(oldHistory.get(index));
        for (JsonNode row : newHistory) assertThat(row.path("success").asBoolean()).isTrue();
        assertThat(after.path("tables").path("quarantine_origin").path("columns")).isNotEmpty();
    }

    private static void legacyTables(JsonNode before, JsonNode after) {
        before.fields().forEachRemaining(entry -> {
            String name = entry.getKey(); var old = entry.getValue(); var current = after.path(name);
            assertThat(current.isObject()).as(name).isTrue();
            if (Set.of("permission", "role_permission").contains(name)) {
                assertThat(current.path("columns")).isEqualTo(old.path("columns"));
                assertThat(list(current.path("rows"))).containsAll(list(old.path("rows"))); return;
            }
            if (!name.equals("submitted_capture")) { assertThat(current).as(name).isEqualTo(old); return; }
            var expectedColumns = list(old.path("columns"));
            expectedColumns.add(com.fasterxml.jackson.databind.node.TextNode.valueOf("operation_revision"));
            assertThat(list(current.path("columns"))).isEqualTo(expectedColumns);
            var projected = new ArrayList<List<JsonNode>>();
            for (JsonNode row : current.path("rows")) projected.add(list(row).subList(0, old.path("columns").size()));
            assertThat(projected).containsExactlyInAnyOrderElementsOf(list(old.path("rows")).stream().map(E2aReceivedCheckpointUpgrade::list).toList());
        });
        after.fields().forEachRemaining(entry -> {
            if (!before.has(entry.getKey())) assertThat(entry.getValue().path("rows")).as(entry.getKey()).isEmpty();
        });
    }

    private static void permissionAdditions(JsonNode before, JsonNode after) {
        var expected = Map.of(
            "rescue:authorize", "预授权和撤销指定原件的只读救援", "rescue:read", "作为预先指定主管取回原件",
            "rescue:relay", "同仓受控中转原设备冻结资料", "pda:independent", "预置受限独立采集身份");
        var additions = list(after.path("permission").path("rows")); additions.removeAll(list(before.path("permission").path("rows")));
        assertThat(additions).hasSize(expected.size());
        var ids = new HashMap<String, JsonNode>();
        var codes = List.of("rescue:authorize", "rescue:read", "rescue:relay", "pda:independent");
        for (JsonNode row : additions) {
            String code = decoded(cell(after.path("permission"), row, "code"));
            assertThat(expected).containsKey(code);
            assertThat(decoded(cell(after.path("permission"), row, "name"))).isEqualTo(expected.get(code));
            var id = cell(after.path("permission"), row, "id");
            assertThat(uuid(id)).isEqualTo("019a0000-0000-7000-8000-000000000" + (101 + codes.indexOf(code)));
            assertThat(ids.put(code, id)).isNull();
        }
        var expectedRoles = new ArrayList<List<JsonNode>>();
        for (String code : codes) for (String role : code.equals("pda:independent")
                ? List.of("WAREHOUSE_ADMIN", "PDA_OPERATOR") : List.of("SUPERVISOR"))
            expectedRoles.add(List.of(roleId(after.path("role"), role), ids.get(code)));
        var actual = list(after.path("role_permission").path("rows")); actual.removeAll(list(before.path("role_permission").path("rows")));
        assertThat(actual.stream().map(E2aReceivedCheckpointUpgrade::list).toList()).containsExactlyInAnyOrderElementsOf(expectedRoles);
    }

    private static void captureRevisions(JsonNode tables) {
        var captures = tables.path("submitted_capture"); var submissions = tables.path("submission");
        var assignments = tables.path("order_assignment");
        for (JsonNode row : captures.path("rows")) {
            JsonNode submission = find(submissions, "id", cell(captures, row, "submission_id"));
            JsonNode assignmentId = cell(submissions, submission, "assignment_id");
            JsonNode expected = assignmentId.isNull()
                ? com.fasterxml.jackson.databind.node.TextNode.valueOf("MQ==")
                : cell(assignments, find(assignments, "id", assignmentId), "revision");
            assertThat(cell(captures, row, "operation_revision")).isEqualTo(expected);
        }
    }

    private static JsonNode roleId(JsonNode table, String code) {
        return cell(table, find(table, "code", com.fasterxml.jackson.databind.node.TextNode.valueOf(
            Base64.getEncoder().encodeToString(code.getBytes(StandardCharsets.UTF_8)))), "id");
    }

    private static JsonNode find(JsonNode table, String column, JsonNode value) {
        var found = list(table.path("rows")).stream().filter(row -> cell(table, row, column).equals(value)).toList();
        assertThat(found).hasSize(1); return found.getFirst();
    }

    private static JsonNode cell(JsonNode table, JsonNode row, String column) {
        int index = list(table.path("columns")).indexOf(com.fasterxml.jackson.databind.node.TextNode.valueOf(column));
        assertThat(index).isNotNegative(); return row.get(index);
    }

    private static List<JsonNode> list(JsonNode node) {
        var result = new ArrayList<JsonNode>(); node.forEach(result::add); return result;
    }

    private static String decoded(JsonNode node) { return new String(Base64.getDecoder().decode(node.asText()), StandardCharsets.UTF_8); }
    private static String uuid(JsonNode node) {
        var buffer = java.nio.ByteBuffer.wrap(Base64.getDecoder().decode(node.asText()));
        return new UUID(buffer.getLong(), buffer.getLong()).toString();
    }
    private E2aReceivedCheckpointUpgrade() { }
}
