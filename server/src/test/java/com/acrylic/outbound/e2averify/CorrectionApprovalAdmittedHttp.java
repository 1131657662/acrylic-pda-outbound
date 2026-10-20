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

/** Cookie/CSRF command consumer retaining original responses without tokens or session cookies. */
final class CorrectionApprovalAdmittedHttp {
    private final HttpClient client = HttpClient.newBuilder()
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    private final Path evidence;
    private int calls;

    CorrectionApprovalAdmittedHttp(Path evidence, String username) throws Exception {
        this.evidence = Files.createDirectory(evidence); var token = csrf();
        var bytes = send(request("/auth/web/login").header(token.path("headerName").asText(), token.path("token").asText())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=Test-password-123")), 200);
        assertThat(SubmissionJson.parse(bytes).at("/data/authenticated").asBoolean()).isTrue();
    }

    byte[] command(JsonNode body, int status) throws Exception {
        var token = csrf();
        var request = request("/corrections").header(token.path("headerName").asText(), token.path("token").asText())
            .header("Content-Type", "application/json").header("X-Server-Recovery-Epoch", "1")
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body)));
        return send(request, status);
    }

    private JsonNode csrf() throws Exception {
        return SubmissionJson.parse(send(request("/auth/csrf").GET(), 200)).path("data");
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
        for (String name : List.of("content-type", "cache-control", "x-content-type-options"))
            headers.put(name, response.headers().firstValue(name).orElse(""));
        boolean retained = !path.endsWith("/auth/csrf"); meta.put("bodyRetained", retained);
        if (retained) Files.write(evidence.resolve(prefix + ".body"), response.body(), StandardOpenOption.CREATE_NEW);
        Files.writeString(evidence.resolve(prefix + ".http.json"), meta.toPrettyString(), StandardOpenOption.CREATE_NEW);
        assertThat(response.statusCode()).as("%s %s: %s", request.method(), request.uri(),
            new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(expected);
        if (expected == 200 && path.endsWith("/corrections")) {
            assertThat(headers.path("cache-control").asText()).contains("no-store");
            assertThat(headers.path("x-content-type-options").asText()).isEqualTo("nosniff");
        }
        return response.body();
    }
}
