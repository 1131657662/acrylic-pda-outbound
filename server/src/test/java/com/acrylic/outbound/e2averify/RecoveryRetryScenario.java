package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.RecoveryExportInput;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Actual retry endpoints and persisted export artifacts; no direct product service invocation. */
final class RecoveryRetryScenario {
    record Supply(E2aCrossSourceProfile profile, JdbcTemplate raw, RecoveryExportInput input) { }
    final Supply supply;
    final String mode;
    final RecoveryWorkerHttp http;
    final List<String> originals = new ArrayList<>();

    RecoveryRetryScenario(Supply supply, String mode) throws Exception {
        this.supply = supply; this.mode = mode;
        http = new RecoveryWorkerHttp(supply.profile.attempt.resolve("private/http-retry-" + mode));
    }

    void positive() throws Exception {
        http.get(supply.input.exportPath(), 401); http.login(supply.input.username());
        for (String format : List.of("XLSX", "PDF")) {
            String original = http.create(supply.input.exportPath(), format, UUID.randomUUID().toString(), 200)
                .at("/data/id").asText();
            originals.add(original); preserve(original, mode + "-original-" + format.toLowerCase(Locale.ROOT));
            String key = UUID.randomUUID().toString();
            var retry = http.retry(path(original), key, 200).path("data");
            assertThat(retry.path("retryOf").asText()).isEqualTo(original);
            String id = retry.path("id").asText(); assertThat(id).isNotEqualTo(original);
            assertThat(http.retry(path(original), key, 200).at("/data/id").asText()).isEqualTo(id);
            assertSnapshot(original, id); preserve(id, mode + "-retry-" + format.toLowerCase(Locale.ROOT));
            assertThat(http.create(supply.input.exportPath(), format, key, 409).path("code").asText())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
        }
        http.retry("/exports/" + originals.getFirst() + "/retry?warehouseId=" + UUID.randomUUID(), UUID.randomUUID().toString(), 404);
    }

    void failures() throws Exception {
        var faults = new RecoveryRetryFaults(this);
        faults.concurrent(); faults.rollback(); faults.revokedAfterLockWait();
    }

    String path(String original) { return "/exports/" + original + "/retry?warehouseId=" + supply.input.warehouse(); }

    String userId() {
        return supply.raw.queryForObject("SELECT BIN_TO_UUID(id) FROM app_user WHERE username=?", String.class, supply.input.username());
    }

    JsonNode payload(String id) {
        return SubmissionJson.parse(supply.raw.queryForObject("SELECT payload FROM background_job WHERE id=UUID_TO_BIN(?)", String.class, id));
    }

    void assertSnapshot(String original, String retry) {
        assertThat(payload(retry).path("snapshotHash")).isEqualTo(payload(original).path("snapshotHash"));
        assertThat(payload(retry).path("retryOf").asText()).isEqualTo(original);
    }

    void preserve(String id, String prefix) throws Exception {
        await().atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofMillis(250)).untilAsserted(() ->
            assertThat(supply.raw.queryForObject("SELECT status FROM background_job WHERE id=UUID_TO_BIN(?)", String.class, id))
                .isEqualTo("SUCCEEDED"));
        var view = http.get("/exports/" + id + "?warehouseId=" + supply.input.warehouse(), 200).path("data");
        var payload = payload(id); String extension = payload.path("format").asText().equals("PDF") ? "pdf" : "xlsx";
        Path base = supply.profile.attempt;
        byte[] bytes = http.download(id, supply.input.warehouse(), base.resolve(prefix + "." + extension));
        assertThat(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)))
            .isEqualTo(view.path("sha256").asText());
        var facts = SubmissionJson.object().set("view", view);
        ((com.fasterxml.jackson.databind.node.ObjectNode) facts).set("payload", payload);
        Files.writeString(base.resolve(prefix + ".json"), facts.toPrettyString(), StandardOpenOption.CREATE_NEW);
        Files.copy(base.resolve("storage/exports/snapshots/" + payload.path("snapshotHash").asText() + ".json"),
            base.resolve(prefix + ".snapshot.json"));
    }
}
