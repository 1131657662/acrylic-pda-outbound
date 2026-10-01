package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

/** Real authenticated HTTP with preserved response bytes; no SQL prepares expected contexts. */
final class CorrectionReadHttp {
    private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    private final Path evidence;
    private final String actor;
    private int calls;

    CorrectionReadHttp(String username, Path evidence, String actor) throws Exception {
        this.evidence = evidence; this.actor = actor;
        var csrf = csrf();
        send(request("/auth/web/login").header(csrf.path("headerName").asText(), csrf.path("token").asText())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=Test-password-123")), 200);
    }

    byte[] get(String path, int status) throws Exception { return send(request(path).GET(), status); }

    byte[] command(JsonNode body) throws Exception {
        var csrf = csrf();
        return send(request("/corrections").header(csrf.path("headerName").asText(), csrf.path("token").asText())
            .header("Content-Type", "application/json").header("X-Server-Recovery-Epoch", "1")
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))), 200);
    }

    private JsonNode csrf() throws Exception { return SubmissionJson.parse(get("/auth/csrf", 200)).path("data"); }

    private byte[] send(HttpRequest.Builder builder, int expected) throws Exception {
        var request = builder.build(); var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        boolean business = request.uri().getPath().contains("/corrections");
        if (business) {
            String file = actor + "-" + (++calls) + ".json"; Files.write(evidence.resolve(file), response.body());
            Files.writeString(evidence.resolve("http.tsv"), actor + "\t" + request.method() + "\t" + request.uri().getRawPath()
                + "?" + request.uri().getRawQuery() + "\t" + response.statusCode() + "\t" + file + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        assertThat(response.statusCode()).as("%s %s: %s", request.method(), request.uri().getPath(),
            new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(expected);
        if (business && expected == 200) {
            assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
            assertThat(response.headers().firstValue("X-Content-Type-Options").orElse("")).isEqualTo("nosniff");
        }
        return response.body();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1" + path)).timeout(Duration.ofSeconds(30));
    }
}
