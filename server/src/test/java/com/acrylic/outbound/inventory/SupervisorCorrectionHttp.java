package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

/** A real cookie/CSRF session; preserves correction receipt bytes without an envelope assumption. */
final class SupervisorCorrectionHttp {
    private final HttpClient client = HttpClient.newBuilder()
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

    SupervisorCorrectionHttp(String username) throws Exception {
        var token = csrf();
        var request = request("/auth/web/login").header(token.path("headerName").asText(), token.path("token").asText())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=Test-password-123"));
        assertThat(client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray()).statusCode()).isEqualTo(200);
    }

    byte[] command(JsonNode body, int expected) throws Exception {
        var token = csrf();
        var request = request("/corrections").header(token.path("headerName").asText(), token.path("token").asText())
            .header("Content-Type", "application/json").header("X-Server-Recovery-Epoch", "1")
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body)));
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).as("code=%s", SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo(expected);
        if (expected == 200) assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        return response.body();
    }

    private JsonNode csrf() throws Exception {
        var response = client.send(request("/auth/csrf").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        return SubmissionJson.parse(response.body()).path("data");
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1" + path)).timeout(Duration.ofSeconds(30));
    }
}
