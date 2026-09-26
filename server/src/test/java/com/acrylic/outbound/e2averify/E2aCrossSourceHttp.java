package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

final class E2aCrossSourceHttp {
    final E2aCrossSourceWire wire;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    final String token;
    final Path evidence;
    private int sequence;

    E2aCrossSourceHttp(E2aCrossSourceWire wire, String token, Path evidence) {
        this.wire = wire; this.token = token; this.evidence = evidence;
    }

    byte[] post(String operation, UUID key, JsonNode body) throws Exception {
        var response = request(operation, key, body, token);
        assertThat(response.statusCode()).as(operation + ": " + new String(response.body(), java.nio.charset.StandardCharsets.UTF_8))
            .isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        return response.body();
    }

    void reject(String operation, UUID key, JsonNode body, String code) throws Exception {
        var response = request(operation, key, body, token);
        assertThat(response.statusCode()).as(operation + " rejected").isEqualTo(409);
        assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo(code);
    }

    void unauthorized() throws Exception {
        assertThat(request("open", wire.openKey, wire.open(), null).statusCode()).isEqualTo(401);
    }

    void original(UUID key, byte[] expected) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base() + "receipts/" + key + "/original" + query()))
            .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + token).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        record("original", response);
        assertThat(response.statusCode()).isEqualTo(200); assertThat(response.body()).isEqualTo(expected);
    }

    private HttpResponse<byte[]> request(String operation, UUID key, JsonNode body, String bearer) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base() + operation + query() + "&originalKey=" + key
            + "&openOriginalKey=" + wire.openKey + "&stopOriginalKey=" + wire.stopKey))
            .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
            .header("X-Server-Recovery-Epoch", "1").POST(HttpRequest.BodyPublishers.ofByteArray(E2aCrossSourceFixture.bytes(body)));
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        record(operation, response); return response;
    }

    private void record(String operation, HttpResponse<byte[]> response) throws Exception {
        Files.write(evidence.resolve(String.format("%02d-%s-%d.json", ++sequence, operation, response.statusCode())),
            response.body(), java.nio.file.StandardOpenOption.CREATE_NEW);
    }

    private String query() {
        return "?warehouseId=" + wire.fixture.seed.get("warehouseId") + "&submissionId=" + wire.fixture.submission + "&workRound=1";
    }

    private static String base() { return "http://127.0.0.1:19434/api/v1/pda/temporary-source-evidence/"; }
}
