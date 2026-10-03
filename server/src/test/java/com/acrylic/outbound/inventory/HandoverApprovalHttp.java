package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Cookie/CSRF supervisor session, separate from the originating PDA bearer. */
final class HandoverApprovalHttp {
    private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    private final Path directory;
    private int calls;
    HandoverApprovalHttp(String username, Path directory) throws Exception {
        this.directory = directory;
        var csrf = csrf();
        send(request("/auth/web/login").header(csrf.path("headerName").asText(), csrf.path("token").asText())
            .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(
                "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password=Test-password-123")), 200, null);
    }
    byte[] get(String path) throws Exception { return send(request(path).GET(), 200, null); }
    byte[] command(JsonNode command, int expected, String code) throws Exception { return post("/handover-commands", command, expected, code); }
    byte[] post(String path, JsonNode body, int expected, String code) throws Exception {
        var csrf = csrf();
        return send(request(path).header(csrf.path("headerName").asText(), csrf.path("token").asText())
            .header("Content-Type", "application/json").header("X-Server-Recovery-Epoch", "1")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))), expected, code);
    }
    private JsonNode csrf() throws Exception { return SubmissionJson.parse(get("/auth/csrf")).path("data"); }
    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1" + path)).timeout(java.time.Duration.ofSeconds(30));
    }
    private byte[] send(HttpRequest.Builder builder, int expected, String code) throws Exception {
        var request = builder.build(); var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String name = "approval-http-" + ++calls + "-" + UUID.randomUUID() + ".json";
        Files.write(directory.resolve(name), response.body(), StandardOpenOption.CREATE_NEW);
        Files.writeString(directory.resolve("approval-http.tsv"), request.method() + "\t" + request.uri().getPath() + "\t"
            + response.statusCode() + "\t" + name + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        assertThat(response.statusCode()).as(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(expected);
        if (code != null) assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo(code);
        return response.body();
    }
}
