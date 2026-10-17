package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Real cookie/CSRF HTTP with original bytes retained in this attempt's private evidence tree. */
final class RecoveryWorkerHttp {
    private final HttpClient client = HttpClient.newBuilder()
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    private final Path output;
    private int requests;

    RecoveryWorkerHttp(Path output) throws Exception { this.output = Files.createDirectory(output); }

    void login(String username) throws Exception {
        var csrf = get("/auth/csrf", 200).path("data");
        send(builder("/auth/web/login").header(csrf.path("headerName").asText(), csrf.path("token").asText())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=Test-password-123")), 200);
    }

    JsonNode get(String path, int expected) throws Exception {
        return SubmissionJson.parse(new String(send(builder(path).GET(), expected), StandardCharsets.UTF_8));
    }

    JsonNode create(String path, String format, String key, int expected) throws Exception {
        var csrf = get("/auth/csrf", 200).path("data");
        var body = SubmissionJson.object().put("format", format).put("basis", "ORIGINAL_CONFIRMED");
        var bytes = send(builder(path).header(csrf.path("headerName").asText(), csrf.path("token").asText())
            .header("Content-Type", "application/json").header("Idempotency-Key", key)
            .header("X-Server-Recovery-Epoch", "1")
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))), expected);
        return SubmissionJson.parse(new String(bytes, StandardCharsets.UTF_8));
    }

    byte[] download(String id, String warehouse, Path target) throws Exception {
        byte[] bytes = send(builder("/exports/" + id + "/file?warehouseId=" + warehouse).GET(), 200);
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        return bytes;
    }

    JsonNode retry(String path, String key, int expected) throws Exception {
        var csrf = get("/auth/csrf", 200).path("data");
        var bytes = send(builder(path).header(csrf.path("headerName").asText(), csrf.path("token").asText())
            .header("Content-Type", "application/json").header("Idempotency-Key", key)
            .header("X-Server-Recovery-Epoch", "1")
            .POST(HttpRequest.BodyPublishers.ofString("{}")), expected);
        return SubmissionJson.parse(new String(bytes, StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder builder(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + E2aTestPorts.http() + "/api/v1" + path))
            .timeout(Duration.ofSeconds(30));
    }

    private byte[] send(HttpRequest.Builder builder, int expected) throws Exception {
        var request = builder.build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String prefix = String.format("%03d", ++requests);
        Files.write(output.resolve(prefix + ".body"), response.body(), StandardOpenOption.CREATE_NEW);
        var facts = SubmissionJson.object().put("url", request.uri().toString()).put("method", request.method())
            .put("status", response.statusCode()).put("expected", expected)
            .put("sha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(response.body())));
        Files.writeString(output.resolve(prefix + ".http.json"), facts.toPrettyString(), StandardOpenOption.CREATE_NEW);
        assertThat(response.statusCode()).as("%s %s: %s", request.method(), request.uri(),
            new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(expected);
        return response.body();
    }
}
