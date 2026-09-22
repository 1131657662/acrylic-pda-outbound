package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
        if (!storage.startsWith(this.workspace)) throw new IllegalArgumentException("Storage escapes workspace");
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
        if (!before.path("tables").equals(current.path("tables"))) throw new AssertionError("Legacy table values changed");
        if (!before.path("files").equals(current.path("files"))) throw new AssertionError("Private files changed");
        if (!before.path("schema").equals(current.path("schema"))) throw new AssertionError("Legacy DDL changed");
        JsonNode beforeHistory = before.path("flywayHistory");
        JsonNode currentHistory = current.path("flywayHistory");
        for (JsonNode row : beforeHistory) if (!currentHistory.toString().contains(row.toString())) throw new AssertionError("Flyway history lost");
    }

    private void save(String name, StandardOpenOption option) throws Exception {
        if (!name.matches("[a-z0-9-]+\\.json")) throw new IllegalArgumentException("Invalid snapshot name");
        Path evidence = workspace.resolve(".local/evidence-e2a").normalize();
        if (!evidence.startsWith(workspace)) throw new IllegalArgumentException("Evidence escapes workspace");
        Files.createDirectories(evidence);
        Files.writeString(evidence.resolve(name), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(capture()), option);
    }

    private ObjectNode schema() {
        var result = JSON.createObjectNode();
        for (String table : jdbc.queryForList("SHOW TABLES", String.class)) {
            if (table.equals("flyway_schema_history")) continue;
            result.put(table, jdbc.queryForObject("SHOW CREATE TABLE " + E2aSnapshotRows.identifier(table),
                (rs, row) -> rs.getString(2)));
        }
        return result;
    }
}
