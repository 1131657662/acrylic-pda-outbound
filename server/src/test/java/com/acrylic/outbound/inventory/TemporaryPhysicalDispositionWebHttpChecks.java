package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Real Web-session command proof with a trusted verification fixture and rejected-source release. */
public final class TemporaryPhysicalDispositionWebHttpChecks {
    private static final String BASE = "http://127.0.0.1:19434/api/v1";
    private static final String PASSWORD = "Test-password-123";
    private final JdbcAccess access;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String,String> cookies = new LinkedHashMap<>();
    private String csrfHeader, csrfToken, epoch;

    private TemporaryPhysicalDispositionWebHttpChecks(WebApplicationContext context) { access = new JdbcAccess(context); }
    public static void verify(WebApplicationContext context) throws Exception { new TemporaryPhysicalDispositionWebHttpChecks(context).check(); }

    private void check() throws Exception {
        var ids = access.ids();
        var supervisor = access.createSupervisor(ids.warehouse());
        access.jdbc.update("UPDATE submission SET status='REJECTED',issues_json=? WHERE id=?",
            "{\"issues\":[{\"code\":\"SUPERVISOR_REJECTED\"}]}", Ids.bytes(UUID.fromString(ids.submission())));
        access.jdbc.update("UPDATE source_evidence_verification SET result='VERIFIED_COMPLETE_AT_STOP' WHERE id=?",
            Ids.bytes(UUID.fromString(ids.verification())));
        csrf(); login(supervisor.username()); csrf();
        var command = command(ids, supervisor);
        var requestBody = SubmissionJson.canonical(command);
        java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("user.dir"), "physical-disposition-last-request.json"), requestBody);
        com.acrylic.outbound.temporary.protocol.TemporaryProtocol.command(requestBody.getBytes(StandardCharsets.UTF_8));
        var response = send(request("/temporary-commands").header("Content-Type", "application/json")
            .header(csrfHeader, csrfToken).header("X-Server-Recovery-Epoch", epoch).header("X-Request-Id", UUID.randomUUID().toString())
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8)).build());
        assertThat(response.statusCode()).as(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(200);
        var receipt = SubmissionJson.parse(response.body());
        assertThat(receipt.path("commandType").asText()).isEqualTo("PHYSICAL_DISPOSITION");
        assertThat(access.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE bale_id=?", Integer.class,
            Ids.bytes(UUID.fromString(ids.bale())))).isZero();
        assertThat(access.jdbc.queryForObject("SELECT action FROM temporary_physical_disposition_item", String.class)).isEqualTo("RELEASE");
    }

    private ObjectNode command(DbIds ids, Credentials supervisor) throws Exception {
        var facts = data(get("/temporary-commands/facts?warehouseId=" + ids.warehouse() + "&submissionId=" + ids.submission()), 200);
        var expected = (ObjectNode) facts.path("expected").deepCopy();
        ObjectNode manifest = manifest(ids.bale());
        var evidence = access.evidence(ids.submission());
        expected.put("userAuthVersion", access.authVersion(supervisor.username()));
        expected.putNull("candidateContextSha256"); expected.put("dispositionManifestSha256", SubmissionJson.sha(manifest));
        expected.put("dispositionManifestVersion", "1"); expected.put("evidenceSetId", evidence.setId());
        expected.put("evidenceSetSha256", evidence.setSha256()); expected.put("verificationId", evidence.verificationId());
        expected.put("verificationVersion", evidence.verificationVersion()); expected.put("verificationAlgorithmVersion", evidence.algorithmVersion());
        var command = base(ids, expected).put("reason", "rejected source release");
        command.set("source", access.seedSource(ids.submission())); command.set("dispositionManifest", manifest);
        assertThat(command.path("dispositionManifest").path("items").isArray()).isTrue();
        assertThat(command.path("dispositionManifest").path("items").size()).isEqualTo(1);
        return command;
    }

    private ObjectNode base(DbIds ids, ObjectNode expected) {
        ObjectNode result = SubmissionJson.object().put("commandVersion", 1).put("commandType", "PHYSICAL_DISPOSITION")
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", epoch)
            .put("warehouseId", ids.warehouse()).put("submissionId", ids.submission()).put("workRound", "1")
            .put("authorizationId", ids.authorization()).put("freezeDeviceId", ids.device()).put("sourceRecoveryEpoch", "1");
        result.set("source", SubmissionJson.object().put("kind", "TEMP")); result.set("expected", expected); return result;
    }

    private ObjectNode manifest(String bale) {
        ObjectNode manifest = SubmissionJson.object().put("version", 1);
        manifest.putArray("items").add(SubmissionJson.object().put("baleId", bale).put("disposition", "RELEASE"));
        return manifest;
    }

    private void csrf() throws Exception {
        var response = send(request("/auth/csrf").GET().build());
        assertThat(response.statusCode()).isEqualTo(200);
        var data = SubmissionJson.parse(response.body()).path("data");
        csrfHeader = data.path("headerName").asText(); csrfToken = data.path("token").asText(); epoch = data.path("serverRecoveryEpoch").asText("1");
    }

    private void login(String username) throws Exception {
        String form = "username=" + java.net.URLEncoder.encode(username, StandardCharsets.UTF_8)
            + "&password=" + java.net.URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8) + "&_csrf="
            + java.net.URLEncoder.encode(csrfToken, StandardCharsets.UTF_8);
        var response = send(request("/auth/web/login").header("Content-Type", "application/x-www-form-urlencoded")
            .header(csrfHeader, csrfToken).POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8)).build());
        assertThat(response.statusCode()).isIn(200, 204);
    }

    private JsonNode data(HttpResponse<byte[]> response, int status) {
        assertThat(response.statusCode()).as(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(status);
        return SubmissionJson.parse(response.body()).path("data");
    }

    private HttpRequest.Builder request(String path) {
        var builder = HttpRequest.newBuilder(URI.create(BASE + path));
        if (!cookies.isEmpty()) builder.header("Cookie", String.join("; ", cookies.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue()).toList()));
        return builder;
    }

    private HttpResponse<byte[]> send(HttpRequest request) throws Exception {
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        response.headers().allValues("Set-Cookie").forEach(header -> {
            int split = header.indexOf('='); int end = header.indexOf(';');
            if (split > 0) cookies.put(header.substring(0, split), header.substring(split + 1, end > 0 ? end : header.length()));
        });
        return response;
    }

    private HttpResponse<byte[]> get(String path) throws Exception { return send(request(path).GET().build()); }

    private static final class JdbcAccess {
        final org.springframework.jdbc.core.JdbcTemplate jdbc;
        JdbcAccess(WebApplicationContext context) { jdbc = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class); }
        Credentials createSupervisor(String warehouse) {
            UUID user = UUID.randomUUID(); String username = "physical-" + user;
            String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(PASSWORD);
            jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?, ?,UTC_TIMESTAMP(3))",
                Ids.bytes(user), username, "physical disposition test supervisor", hash);
            UUID role = UUID.randomUUID();
            jdbc.update("INSERT INTO role VALUES(?,?,?)", Ids.bytes(role), "PHYSICAL_" + user.toString().substring(20), "physical disposition test role");
            jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code IN ('order:dispose','temporary_order:confirm')", Ids.bytes(role));
            jdbc.update("INSERT INTO user_role VALUES(?,?)", Ids.bytes(user), Ids.bytes(role));
            jdbc.update("INSERT INTO user_warehouse VALUES(?,UUID_TO_BIN(?))", Ids.bytes(user), warehouse);
            return new Credentials(username);
        }
        JsonNode seedSource(String submission) {
            var row = jdbc.queryForMap("SELECT BIN_TO_UUID(c.creator_device_id) device,BIN_TO_UUID(c.client_order_id) client FROM submission s JOIN submission_scope c ON c.id=s.scope_id WHERE s.id=UUID_TO_BIN(?)", submission);
            return SubmissionJson.object().put("kind", "TEMP").put("creatorDeviceId", row.get("device").toString()).put("clientOrderId", row.get("client").toString());
        }
        String authVersion(String username) { return jdbc.queryForObject("SELECT CAST(auth_version AS CHAR) FROM app_user WHERE username=?", String.class, username); }
        Evidence evidence(String submission) {
            var row = jdbc.queryForMap("SELECT BIN_TO_UUID(es.id) set_id,es.set_sha256, BIN_TO_UUID(ev.id) verification_id, CAST(ev.verification_version AS CHAR) verification_version, CAST(ev.algorithm_version AS CHAR) algorithm_version FROM source_evidence_set es JOIN source_evidence_verification ev ON ev.set_id=es.id WHERE es.submission_id=UUID_TO_BIN(?)", submission);
            return new Evidence(row.get("set_id").toString(), row.get("set_sha256").toString(), row.get("verification_id").toString(), row.get("verification_version").toString(), row.get("algorithm_version").toString());
        }
        DbIds ids() {
            var row = jdbc.queryForMap("SELECT BIN_TO_UUID(sc.warehouse_id) warehouse,BIN_TO_UUID(s.id) submission,BIN_TO_UUID(s.temporary_authorization_id) authorization,BIN_TO_UUID(s.freeze_device_id) device,BIN_TO_UUID(i.bale_id) bale,BIN_TO_UUID(ev.id) verification FROM submission s JOIN submission_scope sc ON sc.id=s.scope_id JOIN source_evidence_item i ON i.submission_id=s.id AND i.scope_id=s.scope_id JOIN source_evidence_verification ev ON ev.submission_id=s.id AND ev.scope_id=s.scope_id WHERE s.status='REVIEW_REQUIRED' AND i.item_kind='SCAN' LIMIT 1");
            return new DbIds((String) row.get("warehouse"), (String) row.get("submission"), (String) row.get("authorization"), (String) row.get("device"), (String) row.get("bale"), (String) row.get("verification"));
        }
    }
    private record Credentials(String username) { }
    private record Evidence(String setId, String setSha256, String verificationId, String verificationVersion, String algorithmVersion) { }
    private record DbIds(String warehouse, String submission, String authorization, String device, String bale, String verification) { }
}
