package com.acrylic.outbound.inventory;

import com.acrylic.outbound.e2averify.E2aSnapshotRows;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Full original rows are retained; only existing session access/expiry timestamps may advance. */
final class ReturnedStockCheckpointEvidence {
    private final JdbcTemplate jdbc;
    private final Path directory;
    private final ObjectNode before;

    ReturnedStockCheckpointEvidence(JdbcTemplate jdbc, Path directory) throws Exception {
        this.jdbc = jdbc; this.directory = directory;
        Files.createDirectories(directory);
        before = new E2aSnapshotRows(jdbc).capture();
        save("before.json", before);
    }

    void preserved() throws Exception {
        var after = new E2aSnapshotRows(jdbc).capture();
        save("after.json", after);
        var expected = stable(before); var actual = stable(after);
        assertThat(actual.size()).as("all tables retained").isEqualTo(expected.size());
        var names = expected.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            assertThat(actual.path(name).equals(expected.path(name))).as("preserved table %s; see original facts", name).isTrue();
        }
    }

    private ObjectNode stable(ObjectNode snapshot) {
        var result = snapshot.deepCopy();
        var names = new ArrayList<String>(); result.fieldNames().forEachRemaining(names::add);
        for (String table : names) {
            if (!table.equalsIgnoreCase("SPRING_SESSION")) continue;
            var columns = result.path(table).path("columns");
            for (int index = 0; index < columns.size(); index++) {
                String name = columns.get(index).asText();
                if (!name.equalsIgnoreCase("LAST_ACCESS_TIME") && !name.equalsIgnoreCase("EXPIRY_TIME")) continue;
                for (var row : result.path(table).path("rows"))
                    ((com.fasterxml.jackson.databind.node.ArrayNode) row).set(index,
                        com.fasterxml.jackson.databind.node.NullNode.instance);
            }
        }
        return result;
    }

    private void save(String name, ObjectNode value) throws Exception {
        Files.writeString(directory.resolve(name), value.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }
}
