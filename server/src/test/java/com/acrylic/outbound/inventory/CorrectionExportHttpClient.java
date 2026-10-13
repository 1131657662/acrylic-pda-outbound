package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.exports.ExportModels.Scope;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Real Web login and CSRF transport for the existing export HTTP entry. */
final class CorrectionExportHttpClient {
    private static final String BASE="http://127.0.0.1:19434/api/v1";
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
    private final UUID warehouse;
    private String csrfHeader,csrfToken,epoch;

    private CorrectionExportHttpClient(UUID warehouse) { this.warehouse=warehouse; }

    static CorrectionExportHttpClient login(WebApplicationContext context,Scope scope) throws Exception {
        var wire=new CorrectionExportHttpClient(scope.warehouse());
        var jdbc=context.getBean(JdbcTemplate.class);
        var user=jdbc.queryForMap("SELECT username,password_hash FROM app_user WHERE id=?",Ids.bytes(scope.actor()));
        String password=DeviceCrypto.secret();
        jdbc.update("UPDATE app_user SET password_hash=? WHERE id=?",
                context.getBean(PasswordEncoder.class).encode(password),Ids.bytes(scope.actor()));
        try { wire.authenticate(user.get("username").toString(),password); }
        finally { jdbc.update("UPDATE app_user SET password_hash=? WHERE id=?",user.get("password_hash"),Ids.bytes(scope.actor())); }
        return wire;
    }

    private void authenticate(String username,String password) throws Exception {
        csrf();
        String form="username="+encoded(username)+"&password="+encoded(password)+"&_csrf="+encoded(csrfToken);
        var response=client.send(request("/auth/web/login").header(csrfHeader,csrfToken)
                .header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).as("real Web login").isEqualTo(200);
        csrf();
    }

    private void csrf() throws Exception {
        var response=client.send(request("/auth/csrf").GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        var root=SubmissionJson.parse(response.body());
        assertThat(response.statusCode()).isEqualTo(200);
        csrfHeader=root.at("/data/headerName").asText();csrfToken=root.at("/data/token").asText();
        epoch=root.path("serverRecoveryEpoch").asText();
        assertThat(csrfHeader).isNotBlank();assertThat(csrfToken).isNotBlank();assertThat(epoch).isNotBlank();
    }

    HttpResponse<byte[]> post(String path,JsonNode body,UUID key) throws Exception {
        return client.send(request(resource(path)).header("Content-Type","application/json")
                .header(csrfHeader,csrfToken).header("X-Server-Recovery-Epoch",epoch)
                .header("Idempotency-Key",key.toString())
                .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    HttpResponse<byte[]> get(String path) throws Exception {
        return client.send(request(resource(path)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    }

    HttpResponse<byte[]> archive(String path,byte[] bytes,UUID key) throws Exception {
        return client.send(request(resource(path)).header("Content-Type","application/zip")
            .header(csrfHeader,csrfToken).header("X-Server-Recovery-Epoch",epoch).header("Idempotency-Key",key.toString())
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),HttpResponse.BodyHandlers.ofByteArray());
    }

    static JsonNode data(HttpResponse<byte[]> response,int expected) {
        assertThat(response.statusCode()).as("HTTP response: %s",new String(response.body(),StandardCharsets.UTF_8))
                .isEqualTo(expected);
        return SubmissionJson.parse(response.body()).path("data");
    }

    private String resource(String path) { return path+(path.contains("?")?"&":"?")+"warehouseId="+warehouse; }
    private HttpRequest.Builder request(String path) { return HttpRequest.newBuilder(URI.create(BASE+path)).timeout(Duration.ofSeconds(45)); }
    private static String encoded(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
}
