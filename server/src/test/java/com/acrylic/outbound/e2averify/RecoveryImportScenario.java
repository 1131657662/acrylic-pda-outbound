package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Actual multipart requests plus raw file/row preservation; no product service calls. */
final class RecoveryImportScenario {
    final E2aCrossSourceProfile profile;
    final JdbcTemplate raw;
    final RecoveryImportInput input;
    final ConfigurableApplicationContext web;
    final RecoveryWorkerHttp http;
    final String mode;

    RecoveryImportScenario(E2aCrossSourceProfile profile, JdbcTemplate raw, RecoveryImportInput input,
            ConfigurableApplicationContext web, String mode) throws Exception {
        this.profile=profile; this.raw=raw; this.input=input; this.web=web; this.mode=mode;
        http = new RecoveryWorkerHttp(profile.attempt.resolve("private/http-import-" + mode));
    }

    void positive() throws Exception {
        http.get("/inventory/imports?warehouseId=" + input.warehouse(), 401); http.login(input.username());
        byte[] bytes = bytes(mode.equals("off") ? 1 : 2); String key = UUID.randomUUID().toString();
        var view = upload(http, bytes, key, 200).path("data"); String id = view.path("id").asText();
        assertThat(upload(http, bytes, key, 200).path("data")).isEqualTo(view);
        assertThat(upload(http, bytes(9), key, 409).path("code").asText()).isEqualTo("IDEMPOTENCY_CONTENT_MISMATCH");
        http.upload(UUID.randomUUID().toString(), bytes, UUID.randomUUID().toString(), 404);
        var viewer = peer("viewer"); viewer.login(input.viewer());
        assertThat(viewer.upload(input.warehouse(), bytes, UUID.randomUUID().toString(), 403).path("code").asText())
            .isEqualTo("PERMISSION_DENIED");
        preserve(id, bytes, mode + "-positive");
    }

    JsonNode upload(RecoveryWorkerHttp client, byte[] bytes, String key, int expected) throws Exception {
        return client.upload(input.warehouse(), bytes, key, expected);
    }

    void preserve(String id, byte[] original, String name) throws Exception {
        var view = http.get("/inventory/imports/" + id + "?warehouseId=" + input.warehouse(), 200).path("data");
        var rows = http.get("/inventory/imports/" + id + "/rows?warehouseId=" + input.warehouse(), 200).path("data");
        assertThat(view.path("validRows").asInt()).isEqualTo(2);
        assertThat(view.path("totalWeightGrams").asText()).isEqualTo("601500");
        assertThat(Files.readAllBytes(file(original))).isEqualTo(original);
        Files.write(profile.attempt.resolve(name + ".sku"), original, StandardOpenOption.CREATE_NEW);
        var facts = SubmissionJson.object().put("id", id).put("fileSha256", ImportFiles.sha256(original));
        facts.set("view", view); facts.set("rows", rows); save(name, facts);
    }

    Path file(byte[] bytes) {
        return profile.attempt.resolve("storage/inventory-imports/" + ImportFiles.sha256(bytes) + ".txt");
    }

    static byte[] bytes(int suffix) {
        return SkuFixture.concat(SkuFixture.row("003B00516" + String.format("%02d", suffix), "300.5"),
            SkuFixture.row("571B00516" + String.format("%02d", suffix), "301"));
    }

    RecoveryWorkerHttp peer(String name) throws Exception {
        return new RecoveryWorkerHttp(profile.attempt.resolve("private/http-import-" + mode + "-" + name));
    }

    void save(String name, JsonNode value) throws Exception {
        Files.writeString(profile.attempt.resolve("import-" + name + ".json"), value.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    void probe(String expected, String label) throws Exception {
        var config = SubmissionJson.parse(Files.readString(profile.attempt.resolve("private/p5-retirement/config.json")));
        var command = List.of(E2aWorktree.ROOT.resolve(".local/r09-control-venv/Scripts/python.exe").toString(),
            E2aWorktree.ROOT.resolve("docs/parallel/R09/tests/d13_writer_probe.py").toString(),
            Path.of(config.path("root").asText()).resolve("writers.lock").toString(), "--expect", expected);
        Path out = profile.attempt.resolve("import-writer-" + label + ".stdout");
        var child = new ProcessBuilder(command).redirectOutput(out.toFile())
            .redirectError(out.resolveSibling(out.getFileName() + ".stderr").toFile()).start();
        assertThat(child.waitFor(15, TimeUnit.SECONDS)).isTrue(); assertThat(child.exitValue()).isZero();
        assertThat(SubmissionJson.parse(Files.readString(out)).path("status").asText()).isEqualTo(expected);
    }
}
