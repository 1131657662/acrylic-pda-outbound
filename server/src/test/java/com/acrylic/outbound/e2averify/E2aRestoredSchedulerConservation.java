package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Only job execution, HTTP audit and sessions may change; all original files remain byte-identical. */
final class E2aRestoredSchedulerConservation {
    static void verify(ObjectNode before, ObjectNode after) {
        Set<String> mutable = Set.of("background_job", "operation_event", "spring_session", "spring_session_attributes");
        assertThat(after.path("tables").size()).isEqualTo(before.path("tables").size());
        before.path("tables").fields().forEachRemaining(entry -> {
            assertThat(after.path("tables").has(entry.getKey())).isTrue();
            if (!mutable.contains(entry.getKey().toLowerCase(Locale.ROOT)))
                assertThat(after.path("tables").path(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());
        });
        assertThat(stableSchema(after)).isEqualTo(stableSchema(before));
        assertThat(after.path("flywayHistory")).isEqualTo(before.path("flywayHistory"));
        before.path("files").fields().forEachRemaining(entry ->
            assertThat(after.path("files").path(entry.getKey())).isEqualTo(entry.getValue()));
    }

    static void retirement(Path attempt, int processes) throws Exception {
        Path home = attempt.resolve("private/p5-retirement");
        var index = SubmissionJson.parse(Files.readString(home.resolve("p5/leases/index.json")));
        assertThat(index.path("entries")).isEmpty();
        try (var files = Files.list(home.resolve("p5/leases"))) {
            assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactly("index.json");
        }
        try (var files = Files.list(home.resolve("p5/processes"))) { assertThat(files.count()).isEqualTo(processes); }
        save(attempt, "retired-" + processes + ".json", (ObjectNode) index);
    }

    static void save(Path attempt, String name, ObjectNode value) throws Exception {
        Files.writeString(attempt.resolve(name), value.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    private static ObjectNode stableSchema(ObjectNode facts) {
        var schema = (ObjectNode) facts.path("schema").deepCopy();
        var names = new ArrayList<String>(); schema.fieldNames().forEachRemaining(names::add);
        for (String name : names) if (name.equalsIgnoreCase("operation_event"))
            schema.put(name, schema.path(name).asText().replaceAll(" AUTO_INCREMENT=[0-9]+", ""));
        return schema;
    }
}
