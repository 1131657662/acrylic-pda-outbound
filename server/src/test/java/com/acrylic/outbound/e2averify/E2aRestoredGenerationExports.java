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

/** A new user query may render original facts; restored old-generation jobs remain invalidated. */
final class E2aRestoredGenerationExports {
    private final E2aCrossSourceProfile profile;
    private final RecoveryExportInput input;
    private final RecoveryWorkerHttp http;

    E2aRestoredGenerationExports(E2aCrossSourceProfile profile, RecoveryExportInput input) throws Exception {
        this.profile = profile; this.input = input;
        http = new RecoveryWorkerHttp(profile.attempt.resolve("private/http-regenerated"));
    }

    void consume(JdbcTemplate target) throws Exception {
        http.get(input.exportPath(), 401); http.login(input.username());
        http.get("/orders/" + input.order() + "/exports?warehouseId=" + UUID.randomUUID(), 404);
        for (String format : List.of("XLSX", "PDF")) {
            var original = (ObjectNode) SubmissionJson.parse(Files.readString(profile.attempt.resolve(
                "pending-" + format.toLowerCase(Locale.ROOT) + ".json")));
            invalidated(target, original);
            var record = regenerate(target, original);
            capture(target, record);
            invalidated(target, original);
        }
        assertThat(target.queryForObject("SELECT COUNT(*) FROM background_job", Integer.class)).isEqualTo(4);
    }

    private void invalidated(JdbcTemplate target, ObjectNode original) throws Exception {
        String id = original.path("id").asText(), format = original.path("format").asText();
        assertThat(payload(target, id)).isEqualTo(original.path("payload"));
        assertThat(target.queryForObject("SELECT status FROM background_job WHERE id=UUID_TO_BIN(?)", String.class, id))
            .isEqualTo("PENDING");
        var view = http.get(view(id), 200).path("data");
        assertThat(view.path("status").asText()).isEqualTo("INVALIDATED");
        assertThat(view.path("attempts").asInt()).isZero();
        assertThat(view.path("downloadable").asBoolean()).isFalse();
        assertThat(http.get("/exports/" + id + "/file?warehouseId=" + input.warehouse(), 409).path("code").asText())
            .isEqualTo("SERVER_RECOVERY_EPOCH_MISMATCH");
        assertThat(http.retry("/exports/" + id + "/retry?warehouseId=" + input.warehouse(),
            UUID.randomUUID().toString(), 409).path("code").asText()).isEqualTo("EXPORT_GENERATION_CHANGED");
        assertThat(http.create(input.exportPath(), format, original.path("key").asText(), 409).path("code").asText())
            .isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    private ObjectNode regenerate(JdbcTemplate target, ObjectNode original) throws Exception {
        var listed = http.get(input.exportPath(), 200);
        String epoch = listed.path("serverRecoveryEpoch").asText(), format = original.path("format").asText();
        assertThat(epoch).isEqualTo(target.queryForObject("SELECT CAST(recovery_epoch AS CHAR) FROM server_state WHERE id=1", String.class));
        assertThat(epoch).isNotEqualTo(original.path("payload").path("epoch").asText());
        String key = UUID.randomUUID().toString();
        String id = http.create(input.exportPath(), format, key, 200).at("/data/id").asText();
        assertThat(id).isNotBlank().isNotEqualTo(original.path("id").asText());
        await().atMost(Duration.ofSeconds(120)).untilAsserted(() -> assertThat(target.queryForObject(
            "SELECT status FROM background_job WHERE id=UUID_TO_BIN(?)", String.class, id)).isEqualTo("SUCCEEDED"));
        assertThat(http.create(input.exportPath(), format, key, 200).at("/data/id").asText()).isEqualTo(id);
        http.create(input.exportPath(), format.equals("PDF") ? "XLSX" : "PDF", key, 409);
        var current = payload(target, id);
        assertThat(current.path("epoch").asText()).isEqualTo(epoch);
        for (String field : List.of("scope", "format", "basis", "revision", "originalDay", "template", "resultVersion", "members"))
            assertThat(current.path(field)).as(field).isEqualTo(original.path("payload").path(field));
        return SubmissionJson.object().put("id", id).put("key", key).put("format", format)
            .put("originalId", original.path("id").asText()).set("payload", current);
    }

    private void capture(JdbcTemplate target, ObjectNode record) throws Exception {
        String id = record.path("id").asText(), format = record.path("format").asText().toLowerCase(Locale.ROOT);
        var view = http.get(view(id), 200).path("data");
        assertThat(view.path("attempts").asInt()).isEqualTo(1);
        assertThat(view.path("downloadable").asBoolean()).isTrue();
        record.set("view", view);
        byte[] bytes = http.download(id, input.warehouse(), profile.attempt.resolve("registered-restored-" + format + "." + format));
        assertThat(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)))
            .isEqualTo(view.path("sha256").asText());
        var snapshot = snapshot(record.path("payload").path("snapshotHash").asText(), true);
        var original = SubmissionJson.parse(Files.readString(profile.attempt.resolve("pending-" + format + ".json")));
        assertThat(snapshot.path("orders")).isEqualTo(snapshot(original.at("/payload/snapshotHash").asText(), false).path("orders"));
        assertThat(payload(target, id)).isEqualTo(record.path("payload"));
        Files.writeString(profile.attempt.resolve("registered-restored-" + format + ".json"), record.toPrettyString(), StandardOpenOption.CREATE_NEW);
        Files.copy(profile.attempt.resolve("restored/storage/exports/snapshots/" + record.at("/payload/snapshotHash").asText() + ".json"),
            profile.attempt.resolve("registered-restored-" + format + ".snapshot.json"));
    }

    private ObjectNode snapshot(String hash, boolean restored) throws Exception {
        assertThat(hash).matches("[a-f0-9]{64}");
        Path root = profile.attempt.resolve(restored ? "restored/storage" : "storage");
        return (ObjectNode) SubmissionJson.parse(Files.readString(root.resolve("exports/snapshots/" + hash + ".json")));
    }

    private static ObjectNode payload(JdbcTemplate jdbc, String id) {
        return (ObjectNode) SubmissionJson.parse(jdbc.queryForObject(
            "SELECT payload FROM background_job WHERE id=UUID_TO_BIN(?)", String.class, id));
    }

    private String view(String id) { return "/exports/" + id + "?warehouseId=" + input.warehouse(); }
}
