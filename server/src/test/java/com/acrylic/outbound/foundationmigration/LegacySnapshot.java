package com.acrylic.outbound.foundationmigration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

public final class LegacySnapshot {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;
    public LegacySnapshot(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void save(String name) throws Exception {
        var root = JSON.createObjectNode();
        root.set("tables", new LegacyRows(jdbc).capture());
        root.set("files", files());
        root.set("schema", schema());
        root.set("migrationHistory", JSON.valueToTree(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")));
        root.put("database", FoundationEnvironment.database());
        Files.createDirectories(FoundationEnvironment.evidence(name).getParent());
        Files.writeString(FoundationEnvironment.evidence(name), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }

    public void assertPreserved(String name) throws Exception {
        assertValuesAndFiles(name);
        var before = JSON.readTree(FoundationEnvironment.evidence(name).toFile());
        new LegacySchema(jdbc).verify(before.path("schema"));
    }

    public void assertValuesAndFiles(String name) throws Exception {
        var before = JSON.readTree(FoundationEnvironment.evidence(name).toFile());
        var source = new LegacyRows(jdbc);
        var names = before.path("tables").fieldNames();
        while (names.hasNext()) {
            String table = names.next();
            var old = before.path("tables").path(table);
            var columns = new ArrayList<String>();
            old.path("columns").forEach(column -> columns.add(column.asText()));
            assertThat(source.rows(table, columns)).as("Original values of %s", table).isEqualTo(old.path("rows"));
        }
        assertThat(files()).as("Original private file bytes").isEqualTo(before.path("files"));
    }

    private ObjectNode schema() {
        var result = JSON.createObjectNode();
        for (String table : jdbc.queryForList("SHOW TABLES", String.class)) {
            String ddl = jdbc.queryForObject("SHOW CREATE TABLE " + LegacyRows.name(table), (rs, n) -> rs.getString(2));
            result.put(table, ddl);
        }
        return result;
    }

    private ObjectNode files() throws Exception {
        var result = JSON.createObjectNode();
        Path root = FoundationEnvironment.storage();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                if (Files.isSymbolicLink(path)) throw new IllegalStateException("Unexpected evidence link");
                var item = result.putObject(root.relativize(path).toString().replace('\\', '/'));
                byte[] bytes = Files.readAllBytes(path);
                item.put("bytes", bytes.length);
                item.put("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            }
        }
        return result;
    }
}
