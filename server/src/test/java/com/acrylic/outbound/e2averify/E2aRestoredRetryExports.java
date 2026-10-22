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

/** Actual HTTP original failures and retry requests, retaining original and downloaded bytes. */
final class E2aRestoredRetryExports {
    private final E2aCrossSourceProfile profile;
    private final RecoveryExportInput input;
    private final Map<String, ObjectNode> originals = new LinkedHashMap<>();

    E2aRestoredRetryExports(E2aCrossSourceProfile profile, RecoveryExportInput input) {
        this.profile = profile; this.input = input;
    }

    void prepare(JdbcTemplate source) throws Exception {
        var http = http("pending"); http.get(input.exportPath(), 401); http.login(input.username());
        for (String format : List.of("XLSX", "PDF")) {
            String key = UUID.randomUUID().toString();
            String id = http.create(input.exportPath(), format, key, 200).at("/data/id").asText();
            assertThat(source.queryForObject("SELECT status FROM background_job WHERE id=UUID_TO_BIN(?)", String.class, id))
                .isEqualTo("PENDING");
            var record = SubmissionJson.object().put("id", id).put("key", key).put("format", format);
            record.set("payload", payload(source, id)); originals.put(format, record);
            save("pending-" + format.toLowerCase(Locale.ROOT), record);
        }
    }

    void failed(JdbcTemplate target) throws Exception {
        var http = http("failed"); http.get(input.exportPath(), 401); http.login(input.username());
        for (var entry : originals.entrySet()) {
            String id = entry.getValue().path("id").asText();
            await().atMost(Duration.ofSeconds(120)).untilAsserted(() -> assertThat(target.queryForObject(
                "SELECT status FROM background_job WHERE id=UUID_TO_BIN(?)", String.class, id)).isEqualTo("FAILED"));
            var view = http.get(view(id), 200).path("data");
            assertThat(view.path("status").asText()).isEqualTo("FAILED");
            assertThat(view.path("attempts").asInt()).isEqualTo(3);
            assertThat(view.path("errorCode").asText()).isEqualTo("EXPORT_RENDER_FAILED");
            assertThat(view.path("downloadable").asBoolean()).isFalse();
            assertThat(payload(target, id)).isEqualTo(entry.getValue().path("payload"));
            assertThat(http.get("/exports/" + id + "/file?warehouseId=" + input.warehouse(), 409).path("code").asText())
                .isEqualTo("EXPORT_NOT_READY");
            assertThat(http.create(input.exportPath(), entry.getKey(), entry.getValue().path("key").asText(), 200)
                .at("/data/id").asText()).isEqualTo(id);
            var record = entry.getValue().deepCopy(); record.set("view", view);
            save("failed-" + entry.getKey().toLowerCase(Locale.ROOT), record);
        }
    }

    void retry(JdbcTemplate target) throws Exception {
        var http = http("successor"); http.get(input.exportPath(), 401); http.login(input.username());
        var first = originals.get("XLSX");
        http.retry("/exports/" + first.path("id").asText() + "/retry?warehouseId=" + UUID.randomUUID(),
            UUID.randomUUID().toString(), 404);
        for (var entry : originals.entrySet()) {
            var original = entry.getValue(); String id = original.path("id").asText();
            String key = UUID.randomUUID().toString();
            String retried = http.retry(retryPath(id), key, 200).at("/data/id").asText();
            assertThat(retried).isNotBlank().isNotEqualTo(id);
            assertThat(http.retry(retryPath(id), key, 200).at("/data/id").asText()).isEqualTo(retried);
            String other = originals.get(entry.getKey().equals("PDF") ? "XLSX" : "PDF").path("id").asText();
            assertThat(http.retry(retryPath(other), key, 409).path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
            capture(target, http, original.deepCopy().put("retryId", retried).put("retryKey", key));
        }
        assertThat(target.queryForObject("SELECT COUNT(*) FROM background_job WHERE job_type='REPORT_RENDER_V1'", Integer.class))
            .isEqualTo(4);
    }

    private void capture(JdbcTemplate target, RecoveryWorkerHttp http, ObjectNode record) throws Exception {
        String id = record.path("retryId").asText(), originalId = record.path("id").asText();
        await().atMost(Duration.ofSeconds(120)).untilAsserted(() -> assertThat(target.queryForObject(
            "SELECT status FROM background_job WHERE id=UUID_TO_BIN(?)", String.class, id)).isEqualTo("SUCCEEDED"));
        var view = http.get(view(id), 200).path("data");
        assertThat(view.path("attempts").asInt()).isEqualTo(1);
        assertThat(view.path("retryOf").asText()).isEqualTo(originalId);
        var current = payload(target, id);
        assertThat(current.path("snapshotHash")).isEqualTo(record.path("payload").path("snapshotHash"));
        assertThat(current.path("retryOf").asText()).isEqualTo(originalId);
        record.set("retryPayload", current); record.set("retryView", view);
        String extension = record.path("format").asText().toLowerCase(Locale.ROOT), prefix = "retry-" + extension;
        byte[] bytes = http.download(id, input.warehouse(), profile.attempt.resolve(prefix + "." + extension));
        assertThat(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)))
            .isEqualTo(view.path("sha256").asText());
        Files.copy(profile.attempt.resolve("restored/storage/exports/snapshots/" + current.path("snapshotHash").asText() + ".json"),
            profile.attempt.resolve(prefix + ".snapshot.json"));
        save(prefix, record);
    }

    private ObjectNode payload(JdbcTemplate jdbc, String id) {
        return (ObjectNode) SubmissionJson.parse(jdbc.queryForObject("SELECT payload FROM background_job WHERE id=UUID_TO_BIN(?)", String.class, id));
    }

    private RecoveryWorkerHttp http(String phase) throws Exception {
        return new RecoveryWorkerHttp(profile.attempt.resolve("private/http-" + phase));
    }

    private String view(String id) { return "/exports/" + id + "?warehouseId=" + input.warehouse(); }
    private String retryPath(String id) { return "/exports/" + id + "/retry?warehouseId=" + input.warehouse(); }
    private void save(String name, ObjectNode record) throws Exception {
        Files.writeString(profile.attempt.resolve(name + ".json"), record.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }
}
