package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

/** Cookie/CSRF consumer of the original command response, never a service invocation. */
final class TemporaryExclusionHttp {
    private final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL))
        .connectTimeout(Duration.ofSeconds(10)).build();
    private JsonNode commandCsrf;
    void login(String username) throws Exception {
        var csrf=get("/auth/csrf").path("data");
        var request=builder("/auth/web/login").header(csrf.path("headerName").asText(),csrf.path("token").asText())
            .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(
                "username="+URLEncoder.encode(username,StandardCharsets.UTF_8)+"&password=Test-password-123"));
        assertThat(client.send(request.build(),HttpResponse.BodyHandlers.ofByteArray()).statusCode()).isEqualTo(200);
        commandCsrf=get("/auth/csrf").path("data");
    }
    HttpResponse<byte[]> send(JsonNode command) throws Exception {
        var csrf=commandCsrf;
        return client.send(builder("/temporary-commands").header(csrf.path("headerName").asText(),csrf.path("token").asText())
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(command)))
            .build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    byte[] post(JsonNode command) throws Exception {
        var response=send(command);
        assertThat(response.statusCode()).as("HTTP command: %s",SubmissionJson.parse(response.body()).path("code")).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");return response.body();
    }
    byte[] original(String path) throws Exception {
        var response=client.send(builder(path).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).as("original HTTP status").isEqualTo(200);return response.body();
    }
    JsonNode withdrawal(String path,JsonNode body) throws Exception {
        var csrf=commandCsrf;
        var response=client.send(builder(path).header(csrf.path("headerName").asText(),csrf.path("token").asText())
            .header("Content-Type","application/json").header("Idempotency-Key",java.util.UUID.randomUUID().toString())
            .header("X-Server-Recovery-Epoch","1").POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body)))
            .build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).as("withdrawal code %s",SubmissionJson.parse(response.body()).path("code")).isEqualTo(200);
        return SubmissionJson.parse(response.body()).path("data");
    }
    private JsonNode get(String path) throws Exception {
        var response=client.send(builder(path).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);return SubmissionJson.parse(response.body());
    }
    private HttpRequest.Builder builder(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1"+path)).timeout(Duration.ofSeconds(30));
    }
}
