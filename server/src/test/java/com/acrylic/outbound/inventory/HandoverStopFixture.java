package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.RequestIdFilter;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

/** Real publication/claim/recall handlers with generated test EC identities; stop transport is real loopback HTTP. */
final class HandoverStopFixture {
    final JdbcTemplate jdbc;
    final RecallFixture r;
    final UUID kase;
    final ObjectNode command;
    final Path directory;
    private final HttpClient client = HttpClient.newHttpClient();
    private final AtomicInteger requests = new AtomicInteger();

    HandoverStopFixture(WebApplicationContext app, Path directory) throws Exception { this(app, directory, null); }

    HandoverStopFixture(WebApplicationContext app, Path directory, HandoverStopFixture prior) throws Exception {
        this.directory = Files.createDirectory(directory); jdbc = app.getBean(JdbcTemplate.class);
        r = source(app, prior); var web = r.f.web;
        r.start(false); assertThat(r.f.submit().path("status").asText()).isEqualTo("REVIEW_REQUIRED");
        r.upload();
        String scope = jdbc.queryForObject("SELECT BIN_TO_UUID(scope_id) FROM submission WHERE id=UUID_TO_BIN(?)", String.class, r.f.id());
        var context = SubmissionJson.parse(web.send(get("/api/v1/handovers/scopes/" + scope + "/context"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        var request = SubmissionJson.object().put("commandVersion", 1).put("commandType", "REQUEST")
            .put("commandId", UUID.randomUUID().toString()).put("warehouseId", web.identity().warehouse().toString())
            .put("scopeId", scope).put("transportRecoveryEpoch", "1").put("reason", "原机受控交接停止测试");
        request.set("expected", context.path("expected"));
        var receipt = SubmissionJson.parse(web.send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post("/api/v1/handover-commands").contentType("application/json").content(request.toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        kase = UUID.fromString(receipt.path("businessDecision").path("caseId").asText());
        command = r.ackBody().put("commandId", UUID.randomUUID().toString()).put("expectedCaseVersion", "1")
            .put("changeRequestId", r.recall.path("changeRequestId").asText());
        Files.writeString(directory.resolve("command.json"), command.toPrettyString());
    }

    private RecallFixture source(WebApplicationContext app, HandoverStopFixture prior) throws Exception {
        if (prior != null) return new RecallFixture(new SubmissionFixture(prior.r.f));
        var mvc = MockMvcBuilders.webAppContextSetup(app).addFilters(app.getBean(RequestIdFilter.class)).apply(springSecurity()).build();
        var identity = IdentityFixture.create(jdbc, app.getBean(PasswordEncoder.class), "WAREHOUSE_ADMIN");
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='SUPERVISOR'", Ids.bytes(identity.user().id()));
        var web = new PublicationDriver(mvc, app.getBean(ObjectMapper.class), identity);
        return new RecallFixture(new SubmissionFixture(web, jdbc, true, false));
    }

    byte[] post(ObjectNode body, JsonNode tokens, int expected, String code) throws Exception { return postTo(kase, body, tokens, expected, code); }

    byte[] postTo(UUID caseId, ObjectNode body, JsonNode tokens, int expected, String code) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/handovers/" + caseId + "/stop-ack"))
            .timeout(java.time.Duration.ofSeconds(30)).header("Content-Type", "application/json").header("X-Server-Recovery-Epoch", "1");
        if (tokens != null) builder.header("Authorization", "Bearer " + tokens.path("accessToken").asText());
        var response = client.send(builder.POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))).build(), HttpResponse.BodyHandlers.ofByteArray());
        int number = requests.incrementAndGet(); Files.write(directory.resolve("http-" + number + ".json"), response.body());
        assertThat(response.statusCode()).as(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(expected);
        if (code != null) assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo(code);
        if (expected == 200) {
            assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
            assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        }
        return response.body();
    }
}