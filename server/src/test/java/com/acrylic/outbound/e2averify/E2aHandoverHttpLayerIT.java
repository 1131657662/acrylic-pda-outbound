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
 * behaviour: switch off must be an absent route (404), no session must be 401, a session without any handover
 * permission must be 403, and a session with one must reach the business logic (200/400/404/409, never 401/403).
 *
 * <p>Note on 403: at the HTTP layer a CSRF failure and a permission denial are both 403. This test therefore checks
 * the response body to tell which one it observed ({@code ACCESS_DENIED} from the security handler vs
 * {@code PERMISSION_DENIED} from the handover identity gate) instead of silently accepting either.
 *
 * <p>请求体必须锚定<b>真实来源</b>：{@code HandoverCommandService.executeOnce} 先取 {@code scopeFacts}/{@code caseFacts}
 * 再进身份门，全零 scope/caseId 只会在 {@code HandoverCommand.Locks} 处得 404，权限门根本触达不到（attempt 285 的 ③即此）。
 * 故本夹具按 V1-V34 既有表插入一条 FORMAL 来源（{@code source_order_id} 非空、{@code canonical_order_id} 必须为 NULL，
 * 见 {@code ck_submission_scope_source}）与一条 {@code handover_case}，仅用于让 ③/④/⑤ 走到身份门与业务层。
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverHttpLayerIT {
    private static final String BASE = "http://127.0.0.1:19434/api/v1";
    private static final String HANDOVER = "order:handover";
    private static final String RESUME = "order:resume";
    /** The probe users must hold a real credential, otherwise the web login never succeeds. */
    private static final String HASH = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode("Test-password-123");

    @Test void handoverRouteIsInterceptedAtTheHttpLayer() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        // 必须迁到 V44：`order:handover` 由 V44 才注册进 permission 表。只迁到 V42 时 `role_permission` 的授予会静默
        // 插入 0 行、任何会话都没有该权限（attempt 162 的老陷阱；本次 attempt 373 实测 `v44=[]`、`handoverOnlyPerms=[]`）。
        profile.migrate(jdbc, "44"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        var notes = new StringBuilder("验证层级=HTTP 层（真服 19434 + 真 HTTP）\n");

        // The actor set is written into the database the server will use; no business decision is attempted, the
        // interception layer and the permission gate are what is being observed.
        String warehouse = jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM warehouse LIMIT 1", String.class);
        String approver = user(jdbc, warehouse, HANDOVER, RESUME);
        String resumeOnly = user(jdbc, warehouse, RESUME);
        String outsider = user(jdbc, warehouse);
        // 真实来源夹具：REQUEST 需要 scope 锚点，⑤ 需要一条能过状态门的 case 行。
        var requestScope = formalScope(jdbc, warehouse);
        var approveScope = formalScope(jdbc, warehouse);
        var approveCase = handoverCase(jdbc, warehouse, approveScope);

        // (a) switch OFF: an authenticated request must find no route at all.
        try (var off = E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=false")) {
            var session = new Session(off.getBean(JdbcTemplate.class));
            int status = session.login(outsider) ? session.postHandover("{}") : -1;
            notes.append("①开关关闭+有会话 -> ").append(status).append(" (期望 404)\n");
            assertThat(status).as("① switch off must be an absent route").isEqualTo(404);
        }

        // (b) switch ON: 401 without a session, 403 without any handover permission, business path with one.
        try (var on = E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=true")) {
            var jdbcOn = on.getBean(JdbcTemplate.class);
            // The CSRF filter runs before authentication, so a token-less POST is denied with 403 and never reaches the
            // 401 entry point. Fetch a token (which /auth/csrf issues to an anonymous session) and only then is "no
            // session" observable as 401 - the check the IT means to make.
            var anonymous = new Session(jdbcOn);
            anonymous.csrf();
            int noSession = anonymous.postHandover("{}");
            notes.append("②开关开启+无会话 -> ").append(noSession).append(" (期望 401)\n");
            assertThat(noSession).as("② no session").isEqualTo(401);

            // ③ 无任何交接权限 + 真实 scope：必须停在身份门（PERMISSION_DENIED），不能是 CSRF 的 ACCESS_DENIED。
            var without = new Session(jdbcOn);
            assertThat(without.login(outsider)).as("outsider login").isTrue();
            var rejected = without.postHandoverResponse(request(requestScope, outsider));
            notes.append("③开关开启+无 order:handover/order:resume -> ").append(rejected.statusCode()).append(' ')
                .append(shortBody(rejected)).append(" (期望 403 PERMISSION_DENIED)\n");
            assertThat(rejected.statusCode()).as("③ permission gate").isEqualTo(403);
            assertThat(rejected.body()).as("③ must be the permission gate, not the CSRF filter")
                .contains("PERMISSION_DENIED").doesNotContain("ACCESS_DENIED");

            // ④ 有 order:handover + 真实 scope：必须进业务层（200/400/404/409，绝不能是 401/403）。
            var with = new Session(jdbcOn);
            assertThat(with.login(approver)).as("approver login").isTrue();
            var reached = with.postHandoverResponse(request(requestScope, approver));
            notes.append("④开关开启+有 order:handover -> ").append(reached.statusCode()).append(' ')
                .append(shortBody(reached)).append(" (期望 200/400/404/409，绝不能是 401/403)\n");
            assertThat(reached.statusCode()).as("④ with permission must reach business logic").isIn(200, 400, 404, 409);

            // ⑤ 权限门不是"按命令拆分"的：仅 order:resume 的会话也过得了 approval 门（mayApprove() 是 HANDOVER||RESUME），
            // 所以 APPROVE 必须落到业务层被判（409），不能是 403 —— 若这里得到 403，说明有人把权限门收窄成了按命令拆分。
            var resumeSession = new Session(jdbcOn);
            assertThat(resumeSession.login(resumeOnly)).as("resume-only login").isTrue();
            var approveReached = resumeSession.postHandoverResponse(approve(approveScope, approveCase, resumeOnly));
            notes.append("⑤开关开启+仅 order:resume 发 APPROVE -> ").append(approveReached.statusCode()).append(' ')
                .append(shortBody(approveReached)).append(" (期望 409 业务拒绝：resume 已过 approval 门，权限不按命令拆分)\n");
            assertThat(approveReached.statusCode()).as("⑤ approval gate accepts order:resume today, so the decision is business-level")
                .isIn(200, 400, 404, 409);
            assertThat(approveReached.body()).as("⑤ must not be refused by the permission/CSRF layer")
                .doesNotContain("PERMISSION_DENIED").doesNotContain("ACCESS_DENIED");

            // ⑥ 反向：#4 的设计语汇是"REQUEST 需 order:resume / APPROVE 需 order:handover"，实测权限门只要求
            // HANDOVER||RESUME（对四种命令一视同仁）⇒ 仅 order:handover 的会话同样能发起 REQUEST。
            var handoverOnly = user(jdbcOn, warehouse, HANDOVER);
            var handoverSession = new Session(jdbcOn);
            assertThat(handoverSession.login(handoverOnly)).as("handover-only login").isTrue();
            var handoverRequest = handoverSession.postHandoverResponse(request(approveScope, handoverOnly));
            notes.append("⑥开关开启+仅 order:handover 发 REQUEST -> ").append(handoverRequest.statusCode()).append(' ')
                .append(shortBody(handoverRequest)).append(" (期望 200/400/404/409：handover 也能过 REQUEST 的权限门)\n");
            assertThat(handoverRequest.statusCode()).as("⑥ REQUEST is not restricted to order:resume in the kernel").isIn(200, 400, 404, 409);
        }

        Files.writeString(profile.attempt.resolve("http-layer.txt"), notes.toString());
        Files.writeString(profile.attempt.resolve("test-completed.marker"), notes.toString());
    }

    /** 一条 FORMAL 来源：自带一张新正式单（{@code source_order_id} 唯一，不能复用既有单）+ 一个该来源自己的提交
     *  （不复用已播种提交——它有证据行挂在 {@code (scope_id,submission_id,work_round)} 上，改归属会撞外键）。 */
    private Scope formalScope(JdbcTemplate jdbc, String warehouse) {
        String scope = Ids.next().toString(), submission = Ids.next().toString(), order = Ids.next().toString(), revision = Ids.next().toString();
        var seed = jdbc.queryForMap("SELECT BIN_TO_UUID(a.id) assignment,BIN_TO_UUID(a.device_id) device,"
            + "BIN_TO_UUID(a.operator_id) operator,CAST(a.revision AS SIGNED) templateRevision,"
            + "BIN_TO_UUID(a.order_id) templateOrder FROM order_assignment a WHERE a.warehouse_id=UUID_TO_BIN(?) LIMIT 1", warehouse);
        jdbc.update("INSERT INTO outbound_order(id,warehouse_id,order_no,source,status,row_version,created_by,created_at,updated_at,assignment_epoch) "
            + "SELECT UUID_TO_BIN(?),warehouse_id,CONCAT('E2A-HL-',LEFT(REPLACE(?,'-',''),20)),'WEB','ASSIGNED',1,created_by,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),1 "
            + "FROM outbound_order WHERE id=UUID_TO_BIN(?)", order, scope, seed.get("templateOrder"));
        jdbc.update("INSERT INTO order_revision(id,order_id,warehouse_id,revision,inventory_snapshot_id,mapping_revision,vehicle_no,container_no,"
            + "notes,planned_total,reason,created_by,created_at) SELECT UUID_TO_BIN(?),UUID_TO_BIN(?),warehouse_id,1,inventory_snapshot_id,"
            + "mapping_revision,vehicle_no,container_no,notes,planned_total,reason,created_by,UTC_TIMESTAMP(3) "
            + "FROM order_revision WHERE order_id=UUID_TO_BIN(?) AND revision=?", revision, order, seed.get("templateOrder"), seed.get("templateRevision"));
        jdbc.update("INSERT INTO order_line(revision_id,delivery_code,mapping_id,product_id,model_snapshot,name_snapshot,planned_count) "
            + "SELECT UUID_TO_BIN(?),delivery_code,mapping_id,product_id,model_snapshot,name_snapshot,planned_count FROM order_line "
            + "WHERE revision_id=(SELECT id FROM order_revision WHERE order_id=UUID_TO_BIN(?) AND revision=?)",
            revision, seed.get("templateOrder"), seed.get("templateRevision"));
        jdbc.update("UPDATE outbound_order SET current_revision=1 WHERE id=UUID_TO_BIN(?)", order);
        jdbc.update("INSERT INTO submission_scope(id,warehouse_id,source_order_id,work_round,canonical_order_id,mapping_version,current_submission_id) "
            + "VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,NULL,0,NULL)", scope, warehouse, order);
        jdbc.update("INSERT INTO submission(id,scope_id,assignment_id,freeze_device_id,operator_id,work_round,source_recovery_epoch,status,created_at,updated_at) "
            + "VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),1,1,'REVIEW_REQUIRED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
            submission, scope, seed.get("assignment"), seed.get("device"), seed.get("operator"));
        jdbc.update("UPDATE submission_scope SET current_submission_id=UUID_TO_BIN(?) WHERE id=UUID_TO_BIN(?)", submission, scope);
        return new Scope(jdbc, scope, order, submission, warehouse);
    }

    /** 一条已存在的交接案件（状态过得了 APPROVE 的状态门），供权限拆分观测使用；不做任何业务断言。 */
    private String handoverCase(JdbcTemplate jdbc, String warehouse, Scope scope) {
        String caseId = Ids.next().toString(), approver = Ids.next().toString();
        jdbc.update("INSERT INTO handover_case(id,warehouse_id,scope_id,submission_id,canonical_order_id,source_kind,"
            + "from_work_round,to_work_round,status,stop_state,integrity_state,original_result_state,physical_state,open_flag,"
            + "requested_by,requested_at,row_version,created_at,updated_at) "
            + "VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'FORMAL',1,2,"
            + "'EVIDENCE_PENDING','REQUESTED','UNKNOWN_HIGH_WATERMARK','NONE_VERIFIED','UNCHECKED',1,"
            + "UUID_TO_BIN(?),UTC_TIMESTAMP(3),1,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
            caseId, warehouse, scope.id(), scope.submission(), scope.order(), approver);
        return caseId;
    }

    /** REQUEST 体：锚定真实 scope 与 scopeFacts 的完整 expected（userAuthVersion 由 actor 事实给出）。 */
    private String request(Scope scope, String username) {
        var expected = "{\"scopeId\":\"" + scope.id() + "\",\"canonicalOrderId\":\"" + scope.order()
            + "\",\"mappingVersion\":\"0\",\"fromWorkRound\":\"1\",\"userAuthVersion\":\""
            + authVersion(scope.jdbc(), username) + "\"}";
        return "{\"commandVersion\":1,\"commandType\":\"REQUEST\",\"commandId\":\"" + UUID.randomUUID()
            + "\",\"transportRecoveryEpoch\":\"1\",\"reason\":\"HTTP 层探测\",\"warehouseId\":\"" + scope.warehouse()
            + "\",\"scopeId\":\"" + scope.id() + "\",\"expected\":" + expected + "}";
    }

    /** APPROVE 体：锚定真实 caseId 与 caseFacts 的 expected；四维证据齐备，只为越过状态门去触 approval 权限门。 */
    private String approve(Scope scope, String caseId, String username) {
        var jdbc = scope.jdbc();
        var row = jdbc.queryForMap("SELECT CAST(row_version AS CHAR) v,status s FROM handover_case WHERE id=UUID_TO_BIN(?)", caseId);
        var expected = "{\"caseId\":\"" + caseId + "\",\"caseVersion\":\"" + row.get("v") + "\",\"status\":\"" + row.get("s")
            + "\",\"userAuthVersion\":\"" + authVersion(jdbc, username) + "\"}";
        return "{\"commandVersion\":1,\"commandType\":\"APPROVE\",\"commandId\":\"" + UUID.randomUUID()
            + "\",\"transportRecoveryEpoch\":\"1\",\"reason\":\"HTTP 层探测\",\"warehouseId\":\"" + scope.warehouse()
            + "\",\"caseId\":\"" + caseId + "\",\"stopState\":\"DEVICE_ACKED\",\"integrityState\":\"COMPLETE\","
            + "\"originalResultState\":\"NONE_VERIFIED\",\"physicalState\":\"MATCHED\",\"inputHighWatermark\":\"0\","
            + "\"checklist\":[],\"expected\":" + expected + "}";
    }

    private static String authVersion(JdbcTemplate jdbc, String username) {
        return jdbc.queryForObject("SELECT CAST(auth_version AS CHAR) FROM app_user WHERE username=?", String.class, username);
    }

    private String user(JdbcTemplate jdbc, String warehouse, String... permissions) {
        UUID id = Ids.next();
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(id), "httplayer-" + id, "HTTP 层探测人员", HASH);
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(id), Ids.bytes(UUID.fromString(warehouse)));
        if (permissions.length > 0) {
            UUID role = Ids.next();
            jdbc.update("INSERT INTO role VALUES(?,?,?)", Ids.bytes(role), "HL_" + role.toString().substring(20), "HTTP 层探测");
            for (String permission : permissions)
                jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code=?", Ids.bytes(role), permission);
            jdbc.update("INSERT INTO user_role VALUES(?,?)", Ids.bytes(id), Ids.bytes(role));
        }
        return "httplayer-" + id;
    }

    /** 夹具用的真实 FORMAL 来源：id/order/submission 均来自既有表，authVersion 查询仍走同一条连接。 */
    private record Scope(JdbcTemplate jdbc, String id, String order, String submission, String warehouse) { }

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
