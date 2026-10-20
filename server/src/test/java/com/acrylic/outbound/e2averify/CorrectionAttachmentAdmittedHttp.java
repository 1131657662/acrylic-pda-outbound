package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Real session/CSRF attachment consumer; preserves business responses without credentials/cookies. */
final class CorrectionAttachmentAdmittedHttp {
    private final HttpClient client = HttpClient.newBuilder()
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    private final Path evidence;
    private int calls;

    CorrectionAttachmentAdmittedHttp(Path evidence, String username) throws Exception {
        this.evidence = Files.createDirectory(evidence);
        if (username == null) return;
        var csrf = csrf();
        byte[] bytes = send(request("/auth/web/login").header(csrf.path("headerName").asText(), csrf.path("token").asText())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=Test-password-123")), 200);
        assertThat(SubmissionJson.parse(bytes).at("/data/authenticated").asBoolean()).isTrue();
    }

    JsonNode upload(String path, byte[] bytes, int status) throws Exception {
        var csrf = csrf();
        return SubmissionJson.parse(send(request(path)
            .header(csrf.path("headerName").asText(), csrf.path("token").asText())
            .header("Content-Type", "application/octet-stream").header("X-Server-Recovery-Epoch", "1")
            .header("X-Content-SHA256", ImportFiles.sha256(bytes))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)), status));
    }

    byte[] command(JsonNode body) throws Exception {
        var csrf = csrf();
        return send(request("/corrections").header(csrf.path("headerName").asText(), csrf.path("token").asText())
            .header("Content-Type", "application/json").header("X-Server-Recovery-Epoch", "1")
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))), 200);
    }

    byte[] get(String path, int status) throws Exception { return send(request(path).GET(), status); }

    private JsonNode csrf() throws Exception {
        return SubmissionJson.parse(get("/auth/csrf", 200)).path("data");
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + E2aTestPorts.http() + "/api/v1" + path))
            .timeout(Duration.ofSeconds(30));
    }

    private byte[] send(HttpRequest.Builder builder, int expected) throws Exception {
        var request = builder.build(); var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String prefix = String.format("%03d", ++calls), path = request.uri().getPath();
        var meta = SubmissionJson.object().put("url", request.uri().toString()).put("method", request.method())
            .put("status", response.statusCode()).put("expected", expected).put("sha256", ImportFiles.sha256(response.body()));
        var headers = meta.putObject("headers");
        for (String name : List.of("content-type", "content-length", "content-disposition", "cache-control", "x-content-type-options"))
            headers.put(name, response.headers().firstValue(name).orElse(""));
        boolean retained = !path.endsWith("/auth/csrf");
        meta.put("bodyRetained", retained);
        if (retained) Files.write(evidence.resolve(prefix + ".body"), response.body(), StandardOpenOption.CREATE_NEW);
        Files.writeString(evidence.resolve(prefix + ".http.json"), meta.toPrettyString(), StandardOpenOption.CREATE_NEW);
        assertThat(response.statusCode()).as("%s %s: %s", request.method(), request.uri(),
            new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(expected);
        if (expected == 200 && path.contains("/corrections")) {
            assertThat(headers.path("cache-control").asText()).contains("no-store");
            assertThat(headers.path("x-content-type-options").asText()).isEqualTo("nosniff");
            if (request.method().equals("GET")) {
                assertThat(headers.path("content-type").asText()).isEqualTo("application/octet-stream");
                assertThat(headers.path("content-length").asText()).isEqualTo(Integer.toString(response.body().length));
                assertThat(headers.path("content-disposition").asText()).contains("attachment; filename=");
            }
        }
        return response.body();
    }
}
