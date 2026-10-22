package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Disposable worker options change; failed job rows and original materials stay immutable. */
final class E2aRestoredRetryChecks {
    static void brokenRenderer(E2aCrossSourceProfile profile, Path home) throws Exception {
        Path renderer = Files.createDirectory(profile.attempt.resolve("broken-renderer"));
        Files.createDirectory(renderer.resolve("lib"));
        Path options = home.resolve("worker-options.json");
        Files.copy(options, home.resolve("worker-options-before-injection.json"));
        var original = SubmissionJson.parse(Files.readString(options));
        var changed = original.deepCopy(); int count = 0;
        for (int index = 0; index < original.size(); index++) {
            if (original.get(index).asText().startsWith("--outbound.exports.renderer-home=")) {
                ((com.fasterxml.jackson.databind.node.ArrayNode) changed).set(index,
                    TextNode.valueOf("--outbound.exports.renderer-home=" + renderer));
                count++;
            }
        }
        assertThat(count).isEqualTo(1);
        Files.writeString(options, changed.toPrettyString(), StandardOpenOption.TRUNCATE_EXISTING);
    }

    static void originalRows(ObjectNode failed, ObjectNode last) {
        var before = failed.path("tables").path("background_job");
        var after = last.path("tables").path("background_job");
        assertThat(after.path("columns")).isEqualTo(before.path("columns"));
        assertThat(before.path("rows").size()).isEqualTo(2);
        assertThat(after.path("rows").size()).isEqualTo(4);
        var rows = new ArrayList<JsonNode>(); after.path("rows").forEach(rows::add);
        before.path("rows").forEach(row -> assertThat(rows).contains(row));
        failed.path("files").fields().forEachRemaining(entry ->
            assertThat(last.path("files").path(entry.getKey())).isEqualTo(entry.getValue()));
    }

    static void dependencies(E2aCrossSourceProfile profile, E2aArchiveSnapshotRestore copier) throws Exception {
        for (String name : List.of("server-private.der", "server-public.der"))
            assertThat(Files.readAllBytes(copier.restored.resolve("config/" + name)))
                .isEqualTo(Files.readAllBytes(profile.attempt.resolve("private/" + name)));
        try (var files = Files.list(profile.attempt.resolve("broken-renderer/lib"))) {
            assertThat(files.count()).isZero();
        }
    }
}
