package com.acrylic.outbound.inventory;

import com.acrylic.outbound.e2averify.E2aTestPorts;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

/** Real cookie session and CSRF over the isolated application TCP listener. */
final class TemporaryCandidateHttp {
    private final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL))
        .connectTimeout(Duration.ofSeconds(10)).build();
    void login(String username) throws Exception {
        var csrf=get("/auth/csrf");
        var request=builder("/auth/web/login").header(csrf.path("headerName").asText(),csrf.path("token").asText())
            .header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("username="+URLEncoder.encode(username,StandardCharsets.UTF_8)+"&password=Test-password-123"));
        exchange(request,200);
    }
    JsonNode get(String path) throws Exception { return exchange(builder(path).GET(),200).path("data"); }
    byte[] original(String path) throws Exception {
        var response=client.send(builder(path).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");return response.body();
    }
    JsonNode error(String path,int status) throws Exception { return exchange(builder(path).GET(),status); }
    JsonNode post(String path,JsonNode body,String key) throws Exception {
        var csrf=get("/auth/csrf");
        return exchange(builder(path).header(csrf.path("headerName").asText(),csrf.path("token").asText())
            .header("Content-Type","application/json").header("Idempotency-Key",key).header("X-Server-Recovery-Epoch","1")
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))),200).path("data");
    }
    JsonNode postError(String path,JsonNode body,int status) throws Exception {
        var csrf=get("/auth/csrf");
        return exchange(builder(path).header(csrf.path("headerName").asText(),csrf.path("token").asText())
            .header("Content-Type","application/json").header("Idempotency-Key",java.util.UUID.randomUUID().toString())
            .header("X-Server-Recovery-Epoch","1").POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))),status);
    }
    private HttpRequest.Builder builder(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + E2aTestPorts.http() + "/api/v1"+path)).timeout(Duration.ofSeconds(30));
    }
    private JsonNode exchange(HttpRequest.Builder request,int expected) throws Exception {
        var response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
        var body=SubmissionJson.parse(response.body());
        assertThat(response.statusCode()).as("HTTP status; code=%s",body.path("code").asText()).isEqualTo(expected);return body;
    }
}
