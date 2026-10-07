package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic recipient registration, followed by real HTTP general TEMP preparation and acknowledgement. */
final class HandoverTemporaryTargetFixture {
    final HandoverTemporaryApprovalChecks.TargetContext context;
    final HandoverTemporaryStopFixture source;
    final UUID device, user;
    final String token;
    final JsonNode authorization, manifest;
    final JsonNode session;
    final Path directory;
    private int calls;

    HandoverTemporaryTargetFixture(WebApplicationContext app, HandoverTemporaryApprovalChecks.TargetContext context) throws Exception {
        this.context = context; source = context.source(); directory = Files.createDirectory(source.directory.resolve("target"));
        var identity = IdentityFixture.create(source.jdbc, app.getBean(PasswordEncoder.class), "WAREHOUSE_ADMIN"); user = identity.user().id();
        source.jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(user), Ids.bytes(source.head.warehouse()));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(app)
            .addFilters(app.getBean(com.acrylic.outbound.common.RequestIdFilter.class))
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var setup = new TemporaryFixture(new PublicationDriver(mvc, app.getBean(com.fasterxml.jackson.databind.ObjectMapper.class),
            new IdentityFixture(identity.user(), source.head.warehouse())), source.jdbc);
        device = UUID.fromString(setup.device.id()); token = setup.tokens.path("accessToken").asText();
        session = setup.tokens.deepCopy();
        authorization = SubmissionJson.parse(post("/temporary-preparations", setup.request(), 200)).path("data");
        manifest = SubmissionJson.parse(authorization.path("manifest").asText());
    }

    void acknowledge() throws Exception {
        for (var part : manifest.path("parts")) get("/temporary-preparations/" + authorization.path("authorizationId").asText()
            + "/content/" + part.path("name").asText() + "?bundleId=" + authorization.path("bundleId").asText(), token, 200, null);
        var body = SubmissionJson.object().put("bundleId", authorization.path("bundleId").asText())
            .put("manifestSha256", authorization.path("manifestSha256").asText())
            .put("authorizationEpoch", manifest.path("identity").path("authorizationEpoch").asText()).put("activatedAt", Instant.now().toString());
        post("/temporary-preparations/" + authorization.path("authorizationId").asText() + "/download-ack", body, 200);
    }

    ObjectNode command() throws Exception {
        var view = SubmissionJson.parse(context.reviewer().get("/handovers/" + source.kase + "/context?warehouseId="
            + source.head.warehouse() + "&commandType=PREPARE"));
        var command = SubmissionJson.object().put("commandVersion", 1).put("commandType", "PREPARE")
            .put("commandId", UUID.randomUUID().toString()).put("warehouseId", source.head.warehouse().toString())
            .put("caseId", source.kase.toString()).put("transportRecoveryEpoch", "1").put("reason", "接收机核对原来源准备")
            .put("targetDeviceId", device.toString()).put("targetUserId", user.toString())
            .put("targetAuthorizationId", authorization.path("authorizationId").asText());
        command.set("expected", view.path("expected")); return command;
    }

    byte[] get(String path, String bearer, int status, String code) throws Exception {
        return send(request(path, bearer).GET(), status, code);
    }

    private byte[] post(String path, JsonNode body, int status) throws Exception {
        return send(request(path, token).header("Idempotency-Key", UUID.randomUUID().toString())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))), status, null);
    }

    private HttpRequest.Builder request(String path, String bearer) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda" + path))
            .timeout(java.time.Duration.ofSeconds(30)).header("X-Server-Recovery-Epoch", "1").header("Authorization", "Bearer " + bearer);
    }

    private byte[] send(HttpRequest.Builder builder, int status, String code) throws Exception {
        var request = builder.build(); var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        int call = ++calls; Files.write(directory.resolve("response-" + call + ".bin"), response.body());
        Files.writeString(directory.resolve("http.tsv"), call + "\t" + request.method() + "\t" + request.uri().getPath()
            + "\t" + response.statusCode() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        assertThat(response.statusCode()).as(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(status);
        if (code != null) assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo(code);
        if (status == 200) assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        return response.body();
    }
}
