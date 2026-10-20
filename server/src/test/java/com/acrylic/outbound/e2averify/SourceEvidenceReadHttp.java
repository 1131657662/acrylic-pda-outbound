package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

/** Read-only source-evidence TCP consumer; credentials and cookies are never persisted. */
final class SourceEvidenceReadHttp {
    private final HttpClient client = HttpClient.newBuilder()
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    private final Path evidence;
    private int calls;

    SourceEvidenceReadHttp(Path evidence, String username) throws Exception {
        this.evidence = Files.createDirectory(evidence);
        if (username == null) return;
        var csrf = SubmissionJson.parse(get("/auth/csrf", 200)).path("data");
        var response = send(request("/auth/web/login").header(csrf.path("headerName").asText(), csrf.path("token").asText())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=Test-password-123")), 200);
        assertThat(SubmissionJson.parse(response).at("/data/authenticated").asBoolean()).isTrue();
    }

    byte[] get(String path, int status) throws Exception { return send(request(path).GET(), status); }
    JsonNode detail(String path, int status) throws Exception { return SubmissionJson.parse(get(path, status)).path("data"); }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + E2aTestPorts.http() + "/api/v1" + path))
            .timeout(Duration.ofSeconds(30));
    }

    private byte[] send(HttpRequest.Builder builder, int expected) throws Exception {
        var request = builder.build(); var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String prefix = String.format("%03d", ++calls);
        Files.write(evidence.resolve(prefix + ".body"), response.body(), StandardOpenOption.CREATE_NEW);
        var meta = SubmissionJson.object().put("path", request.uri().getRawPath() + "?" + request.uri().getRawQuery())
            .put("method", request.method()).put("status", response.statusCode()).put("expected", expected)
            .put("sha256", ImportFiles.sha256(response.body()));
        var headers = meta.putObject("headers");
        for (String key : java.util.List.of("content-type", "cache-control", "x-content-type-options"))
            headers.put(key, response.headers().firstValue(key).orElse(""));
        Files.writeString(evidence.resolve(prefix + ".http.json"), meta.toPrettyString(), StandardOpenOption.CREATE_NEW);
        assertThat(response.statusCode()).as(request.uri() + ": " + new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(expected);
        if (expected == 200 && request.uri().getPath().contains("temporary-source-evidence")) {
            assertThat(headers.path("cache-control").asText()).contains("no-store");
            assertThat(headers.path("x-content-type-options").asText()).isEqualTo("nosniff");
        }
        return response.body();
    }
}
