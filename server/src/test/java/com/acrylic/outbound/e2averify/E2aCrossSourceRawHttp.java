package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Synthetic identity, real TCP HTTP and protected nonempty MySQL; never bypasses RAW intake. */
final class E2aCrossSourceRawHttp {
    private final E2aCrossSourceHttp http;
    private final E2aCrossSourceHttpSetup setup;
    private final HttpClient client = HttpClient.newHttpClient();
    private int sequence;

    E2aCrossSourceRawHttp(E2aCrossSourceHttp http, E2aCrossSourceHttpSetup setup) {
        this.http = http; this.setup = setup;
    }

    void beforeOpen() throws Exception {
        reject(send(0, setup.rawBytes().getFirst(), Map.of(), "1", http.token), 404, "E2A_OPEN_NOT_FOUND");
        assertCount(0);
    }

    HttpResponse<byte[]> first() throws Exception {
        return send(0, setup.rawBytes().getFirst(), Map.of(), "1", http.token);
    }

    void upload() throws Exception {
        negatives();
        var records = setup.rawBytes();
        for (int index = 0; index < records.size(); index++) {
            byte[] bytes = records.get(index);
            JsonNode original = accepted(send(index, bytes, Map.of(), "1", http.token));
            // Simulate a lost response by repeating the fixed identity/body without a replacement key.
            assertThat(accepted(send(index, bytes, Map.of(), "1", http.token))).isEqualTo(original);
            byte[] changed = "different immutable bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            reject(send(index, changed, Map.of("byteCount", "" + changed.length,
                "bytesSha256", ImportFiles.sha256(changed)), "1", http.token), 409, "E2A_RAW_OBJECT_CONFLICT");
        }
        assertCount(records.size());
        assertThat(setup.fixture.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE bale_id IN (?,?)",
            Integer.class, Ids.bytes(setup.fixture.ownBale), Ids.bytes(setup.fixture.priorBale))).isZero();
    }

    private void negatives() throws Exception {
        byte[] body = setup.rawBytes().getFirst();
        assertThat(send(0, body, Map.of(), "1", null).statusCode()).isEqualTo(401);
        reject(send(0, body, Map.of("kind", "STOP_PROOF"), "1", http.token), 400, "E2A_RAW_OBJECT_REQUEST");
        reject(send(0, body, Map.of("byteCount", "33554433"), "1", http.token), 400, "E2A_RAW_OBJECT_REQUEST");
        reject(send(0, body, Map.of("bytesSha256", "0".repeat(64)), "1", http.token), 409, "E2A_RAW_OBJECT_FILE_UNAVAILABLE");
        reject(send(0, body, Map.of("warehouseId", UUID.randomUUID().toString()), "1", http.token), 404, "E2A_SOURCE_NOT_FOUND");
        assertThat(send(0, body, Map.of(), "0", http.token).statusCode()).isEqualTo(409);
        assertCount(0);
    }

    private JsonNode accepted(HttpResponse<byte[]> response) {
        assertThat(response.statusCode()).as(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        var data = SubmissionJson.parse(response.body()).path("data");
        assertThat(data.path("status").asText()).isEqualTo("STORED");
        assertThat(data.path("localDeletionAllowed").booleanValue()).isFalse();
        assertThat(data.has("signedReceipt")).isFalse();
        assertThat(data.has("fileKey")).isFalse();
        return data;
    }

    private void reject(HttpResponse<byte[]> response, int status, String code) {
        assertThat(response.statusCode()).as(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(status);
        assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo(code);
    }

    private HttpResponse<byte[]> send(int index, byte[] body, Map<String, String> overrides, String epoch,
        String token) throws Exception {
        E2aPageItem item = setup.items().get(index);
        var params = new LinkedHashMap<>(Map.of("warehouseId", setup.fixture.seed.get("warehouseId"),
            "submissionId", setup.fixture.submission.toString(), "workRound", "1",
            "openOriginalKey", http.wire.openKey.toString(), "originalObjectId", item.originalObjectId().toString(),
            "kind", item.objectKind(), "byteCount", "" + item.byteCount(), "bytesSha256", item.itemBytesSha256()));
        params.putAll(overrides);
        String query = params.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(java.util.stream.Collectors.joining("&"));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/temporary-source-evidence/raw?" + query))
            .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/octet-stream")
            .header("X-Server-Recovery-Epoch", epoch).POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        Files.write(http.evidence.resolve(String.format("raw-%02d-%d.json", ++sequence, response.statusCode())),
            response.body(), StandardOpenOption.CREATE_NEW);
        return response;
    }

    private void assertCount(int expected) {
        assertThat(setup.fixture.jdbc.queryForObject("SELECT COUNT(*) FROM source_evidence_object WHERE submission_id=?",
            Integer.class, Ids.bytes(setup.fixture.submission))).isEqualTo(expected);
    }
}
