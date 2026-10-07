package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Uses the TEMP source and device STOP previously uploaded by the actual Android HTTP consumer. */
final class HandoverTemporaryStopFixture {
    final JdbcTemplate jdbc;
    final SubmissionStore.Head head;
    final Path directory;
    final HandoverApprovalHttp web;
    final UUID kase;
    final ObjectNode command;
    final String token;
    final String otherDeviceToken;
    private final AtomicInteger calls = new AtomicInteger();

    HandoverTemporaryStopFixture(WebApplicationContext app, Path attempt) throws Exception {
        directory = Files.createDirectory(attempt.resolve("temporary-handover-stop"));
        jdbc = app.getBean(JdbcTemplate.class);
        var result = SubmissionJson.parse(Files.readString(attempt.resolve("android-result.json")));
        head = app.getBean(SubmissionStore.class).required(UUID.fromString(result.path("submissionId").asText()));
        token = SubmissionJson.parse(Files.readString(attempt.resolve("private/android-http-input.json"))).path("accessToken").asText();
        var supervisor = IdentityFixture.create(jdbc, app.getBean(PasswordEncoder.class), "WAREHOUSE_ADMIN");
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='SUPERVISOR'", Ids.bytes(supervisor.user().id()));
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(supervisor.user().id()), Ids.bytes(head.warehouse()));
        web = new HandoverApprovalHttp(supervisor.user().username(), directory);
        otherDeviceToken = otherDevice(app);
        kase = request(); command = stopCommand();
        Files.writeString(directory.resolve("stop-command.json"), SubmissionJson.canonical(command));
    }

    private String otherDevice(WebApplicationContext app) throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(app)
            .addFilters(app.getBean(com.acrylic.outbound.common.RequestIdFilter.class))
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        String username = jdbc.queryForObject("SELECT username FROM app_user WHERE id=?", String.class, Ids.bytes(head.actor()));
        var user = app.getBean(com.acrylic.outbound.auth.IdentityStore.class).loadUserByUsername(username);
        var driver = new PdaTestDriver(new PublicationDriver(mvc, app.getBean(com.fasterxml.jackson.databind.ObjectMapper.class),
            new IdentityFixture(user, head.warehouse())));
        return driver.login(driver.register()).path("accessToken").asText();
    }

    private UUID request() throws Exception {
        String scope = jdbc.queryForObject("SELECT BIN_TO_UUID(scope_id) FROM submission WHERE id=?", String.class, Ids.bytes(head.control().id()));
        var facts = SubmissionJson.parse(web.get("/handovers/scopes/" + scope + "/context?warehouseId=" + head.warehouse()));
        var body = SubmissionJson.object().put("commandVersion", 1).put("commandType", "REQUEST")
            .put("commandId", UUID.randomUUID().toString()).put("warehouseId", head.warehouse().toString())
            .put("scopeId", scope).put("transportRecoveryEpoch", "1").put("reason", "TEMP 原机交接证据验证");
        body.set("expected", facts.path("expected"));
        Files.writeString(directory.resolve("request.json"), SubmissionJson.canonical(body));
        byte[] receipt = web.command(body, 200, null);
        assertThat(web.command(body, 200, null)).isEqualTo(receipt);
        assertThat(SubmissionJson.parse(receipt).path("caseContext").path("sourceKind").asText()).isEqualTo("TEMP");
        return UUID.fromString(SubmissionJson.parse(receipt).path("businessDecision").path("caseId").asText());
    }

    private ObjectNode stopCommand() {
        var row = jdbc.queryForMap("""
            SELECT BIN_TO_UUID(v.id) verification,BIN_TO_UUID(e.id) evidence,e.set_sha256,
              CAST(e.capture_high_watermark AS CHAR) capture,CAST(e.audit_through_seq AS CHAR) audit,
              CAST(e.order_scan_high_watermark AS CHAR) input_high
            FROM source_evidence_set e JOIN source_evidence_verification v ON v.set_id=e.id
            WHERE e.submission_id=? AND v.result='VERIFIED_COMPLETE_AT_STOP'
            """, Ids.bytes(head.control().id()));
        return SubmissionJson.object().put("sourceKind", "TEMP").put("commandId", UUID.randomUUID().toString())
            .put("expectedCaseVersion", "1").put("sourceEvidenceSetId", row.get("evidence").toString())
            .put("verificationId", row.get("verification").toString()).put("setSha256", row.get("set_sha256").toString())
            .put("captureHighWatermark", row.get("capture").toString()).put("auditThroughSeq", row.get("audit").toString())
            .put("orderScanHighWatermark", row.get("input_high").toString());
    }

    void abort() throws Exception {
        var c = head.control();
        var body = SubmissionJson.object().putNull("assignmentId").putNull("continuationId")
            .put("authorizationId", c.temporary().authorization().toString()).put("freezeDeviceId", c.device().toString())
            .put("workRound", Long.toString(c.round())).put("sourceRecoveryEpoch", c.epoch()).put("reason", "原机交接，保留实物占用");
        body.set("source", c.source());
        var receipt = SubmissionJson.parse(post("/submissions/" + c.id() + "/abort", body, token, 200)).path("data");
        assertThat(receipt.path("status").asText()).isEqualTo("ABORTED");
        assertThat(receipt.path("stockReleased").asBoolean()).isFalse();
    }

    byte[] stop(ObjectNode body, int expected, String code) throws Exception {
        byte[] bytes = post("/handovers/" + kase + "/stop-ack", body, token, expected);
        if (code != null) assertThat(SubmissionJson.parse(bytes).path("code").asText()).isEqualTo(code);
        return bytes;
    }

    void sync() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/sync/orders"))
            .timeout(java.time.Duration.ofSeconds(30)).header("X-Server-Recovery-Epoch", "1")
            .header("Authorization", "Bearer " + token).GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        Files.write(directory.resolve("sync-after-stop.json"), response.body());
        assertThat(response.statusCode()).as(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(200);
        var row = jdbc.queryForMap("""
            SELECT o.status,p.status projected,JSON_UNQUOTE(JSON_EXTRACT(x.payload,'$.status')) notified
            FROM outbound_order o JOIN sync_order_projection p ON p.order_id=o.id AND p.warehouse_id=o.warehouse_id
              AND p.recovery_epoch=1
            JOIN order_sync_outbox x ON x.order_id=o.id AND x.event_type='HANDOVER_STOPPED'
            WHERE o.id=? AND JSON_UNQUOTE(JSON_EXTRACT(x.payload,'$.caseId'))=?
            """, Ids.bytes(head.order()), kase.toString());
        assertThat(row.get("status")).isEqualTo("REVIEW_REQUIRED");
        assertThat(row.get("projected")).isEqualTo(row.get("status"));
        assertThat(row.get("notified")).isEqualTo(row.get("status"));
        Files.writeString(directory.resolve("sync-status.json"), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(row));
    }

    byte[] post(String path, JsonNode body, String bearer, int expected) throws Exception {
        int call = calls.incrementAndGet();
        Files.writeString(directory.resolve("pda-request-" + call + ".json"), SubmissionJson.canonical(body));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda" + path))
            .timeout(java.time.Duration.ofSeconds(40)).header("Content-Type", "application/json")
            .header("X-Server-Recovery-Epoch", "1").header("Authorization", "Bearer " + bearer)
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        Files.write(directory.resolve("pda-response-" + call + ".json"), response.body());
        assertThat(response.statusCode()).as(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(expected);
        if (expected == 200 && path.endsWith("/stop-ack")) assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        return response.body();
    }
}
