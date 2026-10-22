package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.RecoveryExportInput;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Real HTTP original jobs stay bound to their restored snapshots across two scheduler lifetimes. */
final class E2aRestoredSchedulerExports {
    private record Consumer(JdbcTemplate jdbc, RecoveryWorkerHttp http, String phase) { }
    private final E2aCrossSourceProfile profile;
    private final RecoveryExportInput input;
    private final Map<String, ObjectNode> originals = new LinkedHashMap<>();

    E2aRestoredSchedulerExports(E2aCrossSourceProfile profile, RecoveryExportInput input) {
        this.profile = profile; this.input = input;
    }

    void prepare(JdbcTemplate source) throws Exception {
        var http = new RecoveryWorkerHttp(profile.attempt.resolve("private/http-pending"));
        http.get(input.exportPath(), 401); http.login(input.username());
        for (String format : List.of("XLSX", "PDF")) {
            String key = UUID.randomUUID().toString();
            String id = http.create(input.exportPath(), format, key, 200).path("data").path("id").asText();
            assertThat(id).isNotBlank();
            assertThat(source.queryForObject("SELECT status FROM background_job WHERE id=UUID_TO_BIN(?)", String.class, id))
                .isEqualTo("PENDING");
            var original = SubmissionJson.object().put("id", id).put("key", key).put("format", format);
            original.set("payload", payload(source, id)); originals.put(format, original);
            save("pending-" + format.toLowerCase(Locale.ROOT) + ".json", original);
        }
        assertThat(source.queryForObject("SELECT COUNT(*) FROM background_job WHERE job_type='REPORT_RENDER_V1'", Integer.class))
            .isEqualTo(2);
    }

    void consume(JdbcTemplate target, String phase) throws Exception {
        var http = new RecoveryWorkerHttp(profile.attempt.resolve("private/http-" + phase));
        http.get(input.exportPath(), 401); http.login(input.username());
        http.get("/orders/" + input.order() + "/exports?warehouseId=" + UUID.randomUUID(), 404);
        var consumer = new Consumer(target, http, phase);
        for (var entry : originals.entrySet()) verify(consumer, entry);
        assertThat(target.queryForObject("SELECT COUNT(*) FROM background_job WHERE job_type='REPORT_RENDER_V1'", Integer.class))
            .isEqualTo(2);
    }

    private void verify(Consumer consumer, Map.Entry<String, ObjectNode> entry) throws Exception {
        var target = consumer.jdbc(); var http = consumer.http();
        String format = entry.getKey(), id = entry.getValue().path("id").asText();
        String key = entry.getValue().path("key").asText();
        await().atMost(Duration.ofSeconds(120)).untilAsserted(() -> assertThat(target.queryForObject(
            "SELECT status FROM background_job WHERE id=UUID_TO_BIN(?)", String.class, id)).isEqualTo("SUCCEEDED"));
        assertThat(http.create(input.exportPath(), format, key, 200).path("data").path("id").asText()).isEqualTo(id);
        http.create(input.exportPath(), format.equals("PDF") ? "XLSX" : "PDF", key, 409);
        var view = http.get("/exports/" + id + "?warehouseId=" + input.warehouse(), 200).path("data");
        assertThat(view.path("attempts").asInt()).isEqualTo(1);
        assertThat(view.path("downloadable").asBoolean()).isTrue();
        var current = payload(target, id); var immutable = current.deepCopy(); immutable.remove("artifact");
        var expected = (ObjectNode) entry.getValue().path("payload").deepCopy(); expected.remove("artifact");
        assertThat(immutable).isEqualTo(expected);
        var record = entry.getValue().deepCopy(); record.set("view", view); record.set("payload", current);
        capture(consumer, record);
    }

    private void capture(Consumer consumer, ObjectNode record) throws Exception {
        String format = record.path("format").asText().toLowerCase(Locale.ROOT), prefix = consumer.phase() + "-" + format;
        byte[] bytes = consumer.http().download(record.path("id").asText(), input.warehouse(), profile.attempt.resolve(prefix + "." + format));
        assertThat(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)))
            .isEqualTo(record.path("view").path("sha256").asText());
        save(prefix + ".json", record);
        Files.copy(profile.attempt.resolve("restored/storage/exports/snapshots/" + record.path("payload").path("snapshotHash").asText() + ".json"),
            profile.attempt.resolve(prefix + ".snapshot.json"));
    }

    private static ObjectNode payload(JdbcTemplate jdbc, String id) {
        return (ObjectNode) SubmissionJson.parse(jdbc.queryForObject(
            "SELECT payload FROM background_job WHERE id=UUID_TO_BIN(?)", String.class, id));
    }

    private void save(String name, ObjectNode value) throws Exception {
        Files.writeString(profile.attempt.resolve(name), value.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }
}
