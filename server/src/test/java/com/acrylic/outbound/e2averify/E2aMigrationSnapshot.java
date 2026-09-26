package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/** Immutable before/after snapshot helper; callers own environment guards and database lifecycle. */
public final class E2aMigrationSnapshot {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;
    private final Path workspace;
    private final Path storage;

    public E2aMigrationSnapshot(JdbcTemplate jdbc, Path workspace, Path storage) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.workspace = workspace.toAbsolutePath().normalize();
        this.storage = storage.toAbsolutePath().normalize();
        requireContained(this.storage, "storage");
    }

    public ObjectNode capture() throws Exception {
        var root = JSON.createObjectNode();
        root.set("tables", new E2aSnapshotRows(jdbc).capture());
        root.set("files", E2aSnapshotFiles.capture(storage, workspace));
        root.set("schema", schema());
        root.set("flywayHistory", JSON.valueToTree(jdbc.queryForList(
            "SELECT * FROM flyway_schema_history ORDER BY installed_rank")));
        root.put("database", jdbc.queryForObject("SELECT DATABASE()", String.class));
        return root;
    }

    public void saveBefore(String name) throws Exception { save(name, StandardOpenOption.CREATE_NEW); }
    public void saveAfter(String name) throws Exception { save(name, StandardOpenOption.CREATE_NEW); }

    public void assertPreserved(JsonNode before) throws Exception {
        if (before == null || !before.path("tables").isObject()) throw new IllegalArgumentException("Invalid before snapshot");
        JsonNode current = capture();
        compareLegacyTables(before.path("tables"), current.path("tables"));
        if (!before.path("files").equals(current.path("files"))) throw new AssertionError("Private files changed");
        compareLegacySchema(before.path("schema"), current.path("schema"));
        JsonNode beforeHistory = before.path("flywayHistory");
        JsonNode currentHistory = current.path("flywayHistory");
        if (currentHistory.size() < beforeHistory.size()) throw new AssertionError("Flyway history shortened");
        for (int index = 0; index < beforeHistory.size(); index++) {
            if (!beforeHistory.get(index).equals(currentHistory.get(index))) throw new AssertionError("Flyway history prefix changed at " + index);
        }
        boolean v35 = false;
        for (JsonNode row : currentHistory) {
            if ("35".equals(row.path("version").asText()) && row.path("success").asBoolean()) v35 = true;
        }
        if (!v35) throw new AssertionError("Successful V35 migration is missing");
    }

    private void save(String name, StandardOpenOption option) throws Exception {
        if (!name.matches("[a-z0-9-]+\\.json")) throw new IllegalArgumentException("Invalid snapshot name");
        Path evidence = workspace.resolve(E2aMigrationEnvironment.EVIDENCE_DIR).normalize();
        requireContained(evidence, "evidence");
        Files.createDirectories(evidence);
        Files.writeString(evidence.resolve(name), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(capture()), option);
    }

    private void compareLegacyTables(JsonNode before, JsonNode current) {
        before.fields().forEachRemaining(entry -> {
            JsonNode old = entry.getValue();
            JsonNode now = current.path(entry.getKey());
            if (!now.isObject()) throw new AssertionError("Legacy table missing: " + entry.getKey());
            List<String> columns = new ArrayList<>(); old.path("columns").forEach(node -> columns.add(node.asText()));
            if (!old.path("rows").equals(now.path("rows"))) throw new AssertionError("Legacy rows changed: " + entry.getKey());
            if (columns.isEmpty()) throw new AssertionError("Legacy table has no columns: " + entry.getKey());
        });
    }

    private void compareLegacySchema(JsonNode before, JsonNode current) {
        before.fields().forEachRemaining(entry -> {
            if (!entry.getValue().equals(current.path(entry.getKey()))) throw new AssertionError("Legacy DDL changed: " + entry.getKey());
        });
    }

    private void requireContained(Path candidate, String label) {
        if (!candidate.startsWith(workspace)) throw new IllegalArgumentException(label + " escapes workspace");
        try {
            Path root = workspace.toRealPath();
            Path existing = Files.exists(candidate) ? candidate.toRealPath() : candidate.getParent().toRealPath().resolve(candidate.getFileName());
            if (!existing.startsWith(root)) throw new IllegalArgumentException(label + " resolves outside workspace");
        } catch (java.io.IOException error) { throw new IllegalArgumentException(label + " cannot be canonicalized", error); }
    }

    private ObjectNode schema() {
        var result = JSON.createObjectNode();
        for (String table : jdbc.queryForList("SHOW TABLES", String.class)) {
            if (table.equals("flyway_schema_history")) continue;
            String ddl = jdbc.queryForObject("SHOW CREATE TABLE " + E2aSnapshotRows.identifier(table),
                (rs, row) -> rs.getString(2));
            result.put(table, ddl);
        }
        return result;
    }
}
