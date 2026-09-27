package com.acrylic.outbound.e2averify;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import com.acrylic.outbound.common.Ids;
import static org.assertj.core.api.Assertions.*;

/**
 * R03 round 10 (HTTP layer, the first real HTTP-layer verification of the R07 command route).
 *
 * <p>It verifies <b>only the interception layer</b> of {@code POST /api/v1/handover-commands}, not any business
 * behaviour: switch off must be an absent route (404), no session must be 401, a session without
 * {@code order:handover} must be 403, and a session with it must reach the business logic (400/404, never 401/403).
 *
 * <p>Note on 403: at the HTTP layer a CSRF failure and a permission denial are both 403. This test therefore logs
 * which one it observed via the response body ({@code ACCESS_DENIED} from the security handler vs
 * {@code PERMISSION_DENIED} from the handover identity gate).
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverHttpLayerIT {
    private static final String BASE = "http://127.0.0.1:19434/api/v1";
    private static final String HANDOVER = "order:handover";
    /** The probe users must hold a real credential, otherwise the web login never succeeds. */
    private static final String HASH = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode("Test-password-123");

    @Test void handoverRouteIsInterceptedAtTheHttpLayer() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "42"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        var notes = new StringBuilder("验证层级=HTTP 层（真服 19434 + 真 HTTP）\n");

        // The actor set is written into the database the server will use; no business source is needed because the
        // interception layer runs before any handover logic.
        String warehouse = jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM warehouse LIMIT 1", String.class);
        String approver = user(jdbc, warehouse, true);
        String outsider = user(jdbc, warehouse, false);

        // (a) switch OFF: an authenticated request must find no route at all.
        try (var off = E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=false")) {
            var session = new Session(off.getBean(JdbcTemplate.class));
            int status = session.login(outsider) ? session.postHandover("{}") : -1;
            notes.append("①开关关闭+有会话 -> ").append(status).append(" (期望 404)\n");
            assertThat(status).as("① switch off must be an absent route").isEqualTo(404);
        }

        // (b) switch ON: 401 without a session, 403 without the permission, business path with it.
        try (var on = E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=true")) {
            // The CSRF filter runs before authentication, so a token-less POST is denied with 403 and never reaches the
            // 401 entry point. Fetch a token (which /auth/csrf issues to an anonymous session) and only then is "no
            // session" observable as 401 - the check the IT means to make.
            var anonymous = new Session(on.getBean(JdbcTemplate.class));
            anonymous.csrf();
            int noSession = anonymous.postHandover("{}");
            notes.append("②开关开启+无会话 -> ").append(noSession).append(" (期望 401)\n");
            assertThat(noSession).as("② no session").isEqualTo(401);

            var without = new Session(on.getBean(JdbcTemplate.class));
            assertThat(without.login(outsider)).as("outsider login").isTrue();
            var rejected = without.postHandoverResponse(body());
            notes.append("③开关开启+无 order:handover -> ").append(rejected.statusCode()).append(' ')
                .append(shortBody(rejected)).append(" (期望 403)\n");
            assertThat(rejected.statusCode()).as("③ permission gate").isEqualTo(403);

            var with = new Session(on.getBean(JdbcTemplate.class));
            assertThat(with.login(approver)).as("approver login").isTrue();
            var reached = with.postHandoverResponse(body());
            notes.append("④开关开启+有 order:handover -> ").append(reached.statusCode()).append(' ')
                .append(shortBody(reached)).append(" (期望 400/404，绝不能是 401/403)\n");
            assertThat(reached.statusCode()).as("④ with permission must reach business logic").isIn(200, 400, 404, 409);
        }

        Files.writeString(profile.attempt.resolve("http-layer.txt"), notes.toString());
        Files.writeString(profile.attempt.resolve("test-completed.marker"), notes.toString());
    }

    /** A syntactically valid REQUEST body with a fresh key; no source exists, so the business layer must refuse it. */
    private String body() {
        return "{\"commandVersion\":1,\"commandType\":\"REQUEST\",\"commandId\":\"" + UUID.randomUUID()
            + "\",\"transportRecoveryEpoch\":\"1\",\"reason\":\"HTTP 层探测\",\"warehouseId\":\""
            + "00000000-0000-0000-0000-000000000000" + "\",\"scopeId\":\"00000000-0000-0000-0000-000000000000\""
            + ",\"expected\":{\"scopeId\":\"00000000-0000-0000-0000-000000000000\"}}";
    }

    private String user(JdbcTemplate jdbc, String warehouse, boolean handover) {
        UUID id = Ids.next();
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(id), "httplayer-" + id, "HTTP 层探测人员", HASH);
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(id), Ids.bytes(UUID.fromString(warehouse)));
        if (handover) {
            UUID role = Ids.next();
            jdbc.update("INSERT INTO role VALUES(?,?,?)", Ids.bytes(role), "HL_" + role.toString().substring(20), "HTTP 层探测");
            jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code=?", Ids.bytes(role), HANDOVER);
            jdbc.update("INSERT INTO user_role VALUES(?,?)", Ids.bytes(id), Ids.bytes(role));
        }
        return "httplayer-" + id;
    }

    private static String shortBody(HttpResponse<String> response) {
        String text = response.body() == null ? "" : response.body().replaceAll("\\s+", " ");
        return text.length() > 160 ? text.substring(0, 160) : text;
    }

    /** A web session over real HTTP; the CSRF token is fetched once before login and once after (session rotation). */
    private static final class Session {
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager())
            .connectTimeout(Duration.ofSeconds(5)).build();
        private final JdbcTemplate jdbc;
        private String header; private String token;
        Session(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        boolean login(String username) throws Exception {
            csrf();
            String form = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode("Test-password-123", StandardCharsets.UTF_8)
                + "&_csrf=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
            var response = client.send(HttpRequest.newBuilder(URI.create(BASE + "/auth/web/login"))
                .header("Content-Type", "application/x-www-form-urlencoded").header(header, token)
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return false;
            csrf(); return true;
        }

        private void csrf() throws Exception {
            var response = client.send(HttpRequest.newBuilder(URI.create(BASE + "/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            var parsed = com.acrylic.outbound.submissions.SubmissionJson.parse(response.body());
            header = parsed.at("/data/headerName").asText(); token = parsed.at("/data/token").asText();
        }

        int postHandover(String body) throws Exception { return postHandoverResponse(body).statusCode(); }

        HttpResponse<String> postHandoverResponse(String body) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(BASE + "/handover-commands"))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (header != null && token != null && !token.isEmpty()) builder.header(header, token);
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
