package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.e2averify.E2aTestPorts;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual Web authentication and binary evidence transport for the checkpoint scenario. */
final class IndependentCheckpointHttp {
    private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
        .connectTimeout(Duration.ofSeconds(5)).build();
    private final UUID warehouse;
    private String csrfHeader, csrfToken, epoch;
    private IndependentCheckpointHttp(UUID warehouse) { this.warehouse = warehouse; }

    static IndependentCheckpointHttp login(WebApplicationContext context, UUID actor, UUID warehouse) throws Exception {
        var wire = new IndependentCheckpointHttp(warehouse); var jdbc = context.getBean(JdbcTemplate.class);
        var user = jdbc.queryForMap("SELECT username,password_hash FROM app_user WHERE id=?", Ids.bytes(actor));
        String password = DeviceCrypto.secret();
        jdbc.update("UPDATE app_user SET password_hash=? WHERE id=?",
            context.getBean(PasswordEncoder.class).encode(password), Ids.bytes(actor));
        try { wire.authenticate(user.get("username").toString(), password); }
        finally { jdbc.update("UPDATE app_user SET password_hash=? WHERE id=?", user.get("password_hash"), Ids.bytes(actor)); }
        return wire;
    }

    private void authenticate(String username, String password) throws Exception {
        csrf();
        String body = "username=" + encode(username) + "&password=" + encode(password) + "&_csrf=" + encode(csrfToken);
        var response = client.send(request("/auth/web/login").header(csrfHeader, csrfToken)
            .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200); csrf();
    }

    private void csrf() throws Exception {
        var response = client.send(request("/auth/csrf").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200); var value = SubmissionJson.parse(response.body());
        csrfHeader = value.at("/data/headerName").asText(); csrfToken = value.at("/data/token").asText();
        epoch = value.path("serverRecoveryEpoch").asText(); assertThat(epoch).isNotBlank();
    }

    HttpResponse<byte[]> get(String path) throws Exception {
        return client.send(request(resource(path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    HttpResponse<byte[]> post(String path, JsonNode body, UUID key) throws Exception {
        return upload(path, SubmissionJson.canonical(body).getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/json", key, null);
    }
    HttpResponse<byte[]> upload(String path, byte[] body, String type, UUID key, String digest) throws Exception {
        var builder = request(resource(path)).header("Content-Type", type).header(csrfHeader, csrfToken)
            .header("X-Server-Recovery-Epoch", epoch).header("Idempotency-Key", key.toString());
        if (digest != null) builder.header("X-Evidence-SHA256", digest);
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private String resource(String path) { return path + (path.contains("?") ? "&" : "?") + "warehouseId=" + warehouse; }
    private HttpRequest.Builder request(String path) { return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + E2aTestPorts.http() + "/api/v1" + path)).timeout(Duration.ofSeconds(45)); }
    private static String encode(String value) { return URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8); }
}
