package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.correction.CorrectionCommandController;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * R07-3 的 **Web 路由真 HTTP** 验证（此前一直记为未验，因为 Web 会话在明文 HTTP 上拿不到会话 Cookie）。
 *
 * <p>层级：真服 19434 + 真 HTTP + 真 MySQL + **Web 会话（登录 + CSRF 令牌 + Cookie 往返）**，不是 PDA Bearer 通道。
 * 这检验两件事：①开关关闭时 `/api/v1/corrections` 不存在（404）；②开关打开时该 Web 路由把请求送进业务层
 * （形状错误 400、订单/文档不存在 404），即**不会**再被 401/403 挡在安全层。
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aCorrectionWebRouteIT {
    private static final String BASE = "http://127.0.0.1:19434/api/v1";
    private static final String HASH =
        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode("Test-password-123");

    @Test void correctionWebRouteIsClosedByDefaultAndReachableOverAWebSession() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "48"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        String warehouse = jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM warehouse LIMIT 1", String.class);
        String corrector = user(jdbc, warehouse, "outbound:correct");
        var notes = new StringBuilder("验证层级=真服 + 真 HTTP + Web 会话（登录/CSRF/Cookie 往返）+ 真 MySQL\n");

        // (a) default switch: the Web route is not assembled at all.
        try (var off = E2aCrossSourceHttpIT.start(profile)) {
            assertThat(off.getBeanNamesForType(CorrectionCommandController.class)).as("默认无路由").isEmpty();
            var session = new Session();
            assertThat(session.login(corrector)).as("corrector login: " + session.last()).isTrue();
            int status = session.postCorrection("{}").statusCode();
            notes.append("①开关关闭 + Web 会话 -> ").append(status).append(" (期望 404)\n");
            assertThat(status).as("① switch off must be an absent route").isEqualTo(404);
        }
        // (b) switch on: the Web session reaches the business layer (no 401/403 in the way).
        try (var on = E2aCrossSourceHttpIT.start(profile, "--outbound.corrections.commands.enabled=true")) {
            assertThat(on.getBeanNamesForType(CorrectionCommandController.class)).hasSize(1);
            var session = new Session();
            assertThat(session.login(corrector)).as("corrector login: " + session.last()).isTrue();
            // (b1) a shape-invalid body is refused by the command kernel, not by the security layer.
            var malformed = session.postCorrection("{}");
            notes.append("②开关开启 + 形状错误正文 -> ").append(malformed.statusCode()).append(' ')
                .append(SubmissionJson.parse(malformed.body()).path("code").asText()).append(" (期望 400)\n");
            assertThat(malformed.statusCode()).as(malformed.body()).isEqualTo(400);
            assertThat(SubmissionJson.parse(malformed.body()).path("code").asText())
                .isEqualTo("INVALID_CORRECTION_COMMAND");
            // (b2) a well-formed command for an unknown order is a business 404, which proves the Web session was
            // authenticated and the kernel ran.
            var command = SubmissionJson.object().put("commandVersion", 1).put("commandType", "CREATE")
                .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
                .put("warehouseId", warehouse).put("orderId", UUID.randomUUID().toString())
                .put("type", "WEIGHT_CORRECTION").put("reason", "Web 路由探测")
                .set("expected", SubmissionJson.object().put("orderId", UUID.randomUUID().toString()));
            var unknown = session.postCorrection(SubmissionJson.canonical(command));
            notes.append("③开关开启 + 不存在的订单 -> ").append(unknown.statusCode()).append(' ')
                .append(SubmissionJson.parse(unknown.body()).path("code").asText()).append(" (期望 404)\n");
            assertThat(unknown.statusCode()).as(unknown.body()).isEqualTo(404);
            assertThat(SubmissionJson.parse(unknown.body()).path("code").asText())
                .isEqualTo("CORRECTION_DOCUMENT_NOT_FOUND");
            notes.append("④结论：Web 会话（登录 + CSRF + Cookie 往返）可写，路由不再被安全层 403 拦住\n");
        }
        Files.writeString(profile.attempt.resolve("correction-web-route.txt"), notes.toString());
        Files.writeString(profile.attempt.resolve("test-completed.marker"), notes.toString());
    }

    private String user(JdbcTemplate jdbc, String warehouse, String permission) {
        UUID id = Ids.next();
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(id), "webcorr-" + id, "Web 路由探测人员", HASH);
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(id), Ids.bytes(UUID.fromString(warehouse)));
        UUID role = Ids.next();
        jdbc.update("INSERT INTO role VALUES(?,?,?)", Ids.bytes(role), "WC_" + role.toString().substring(20), "Web 更正探测");
        jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code=?", Ids.bytes(role), permission);
        jdbc.update("INSERT INTO user_role VALUES(?,?)", Ids.bytes(id), Ids.bytes(role));
        return "webcorr-" + id;
    }

    /** A Web session over real HTTP: CSRF token first, then the form login, then a command POST. */
    private static final class Session {
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager())
            .connectTimeout(Duration.ofSeconds(5)).build();
        private String header; private String token; private String last = "";
        String last() { return last; }

        boolean login(String username) throws Exception {
            csrf();
            var response = postLogin(username);
            if (response.statusCode() == 403) { csrf(); response = postLogin(username); }
            last = response.statusCode() + " " + response.body();
            if (response.statusCode() != 200) return false;
            csrf(); return true;
        }

        private HttpResponse<String> postLogin(String username) throws Exception {
            String form = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode("Test-password-123", StandardCharsets.UTF_8)
                + "&_csrf=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
            return client.send(HttpRequest.newBuilder(URI.create(BASE + "/auth/web/login"))
                .header("Content-Type", "application/x-www-form-urlencoded").header(header, token)
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString());
        }

        private void csrf() throws Exception {
            var response = client.send(HttpRequest.newBuilder(URI.create(BASE + "/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            var parsed = SubmissionJson.parse(response.body());
            header = parsed.at("/data/headerName").asText(); token = parsed.at("/data/token").asText();
        }

        HttpResponse<String> postCorrection(String body) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(BASE + "/corrections"))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (header != null && token != null && !token.isEmpty()) builder.header(header, token);
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
