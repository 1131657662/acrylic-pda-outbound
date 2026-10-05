package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Generated identities and signed credentials; exercises real transaction rollback through MockMvc. */
public final class SubmissionPurposeChecks {
    private final WebApplicationContext app;
    private final JdbcTemplate jdbc;
    private final MockMvc mvc;
    private final Path directory;

    public SubmissionPurposeChecks(WebApplicationContext app, Path attempt) throws Exception {
        this.app = app;
        jdbc = app.getBean(JdbcTemplate.class);
        mvc = MockMvcBuilders.webAppContextSetup(app).addFilters(app.getBean(com.acrylic.outbound.common.RequestIdFilter.class))
            .apply(springSecurity()).build();
        directory = Files.createDirectory(attempt.resolve("submission-purpose"));
    }

    public void verify() throws Exception {
        directSubmissionRejectsImportPurpose();
        malformedPurposesCannotSubmit();
        chunksCannotBypassImportPurpose();
        reviewCannotPromoteImportPurpose();
    }

    private void directSubmissionRejectsImportPurpose() throws Exception {
        var f = fixture(false);
        String original = replacePurpose(f, SubmissionJson.object().put("scope", "pda:handover-import"));
        var body = f.body.deepCopy().put("scope", "pda:scan");
        var result = f.send(body).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("OFFLINE_AUTH_SCOPE_INVALID"));
        Files.writeString(directory.resolve("direct-import.json"), result.andReturn().getResponse().getContentAsString());
        assertNoCommit(f);
        assertThat(Files.exists(directory.getParent().resolve("storage/submissions/" + SubmissionJson.sha(body) + ".json"))).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission WHERE id=?", Integer.class,
            Ids.bytes(UUID.fromString(f.id())))).isZero();
        restore(f, original);
        var receipt = f.submit();
        assertThat(receipt.path("status").asText()).isEqualTo("SUCCEEDED");
        replacePurpose(f, SubmissionJson.object().put("scope", "pda:handover-import"));
        assertThat(f.submit()).isEqualTo(receipt);
        assertThat(f.count("dispatch_record")).isEqualTo(2);
    }

    private void malformedPurposesCannotSubmit() throws Exception {
        var f = fixture(false);
        var arrayScope = SubmissionJson.object();
        arrayScope.putArray("scope").add("pda:scan");
        var variants = List.of(SubmissionJson.object(), SubmissionJson.object().putNull("scope"),
            SubmissionJson.object().put("scope", ""), SubmissionJson.object().put("scope", "pda:scan pda:handover-import"),
            SubmissionJson.object().put("scope", 7), arrayScope);
        String original = f.assignment.path("offlineToken").asText();
        for (var fields : variants) {
            replacePurpose(f, fields);
            f.send(f.body).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFLINE_AUTH_SCOPE_INVALID"));
            assertNoCommit(f);
        }
        restore(f, original);
        assertThat(f.submit().path("status").asText()).isEqualTo("SUCCEEDED");
    }

    private void chunksCannotBypassImportPurpose() throws Exception {
        var f = fixture(false);
        String original = replacePurpose(f, SubmissionJson.object().put("scope", "pda:handover-import"));
        byte[] bytes = SubmissionJson.canonical(f.body).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var control = f.body.deepCopy().put("payloadSha256", SubmissionJson.sha(f.body))
            .put("byteCount", Integer.toString(bytes.length)).put("chunkCount", "1");
        String base = "/api/v1/pda/submissions/" + f.id();
        f.pda.data(post(base + "/upload").header("X-Server-Recovery-Epoch", "1")
            .contentType("application/json").content(control.toString()), f.tokens);
        f.pda.data(post(base + "/chunks/0").header("X-Server-Recovery-Epoch", "1")
            .contentType("application/octet-stream").content(bytes), f.tokens);
        var result = f.pda.send(post(base + "/finalize").header("X-Server-Recovery-Epoch", "1"), f.tokens)
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFLINE_AUTH_SCOPE_INVALID"));
        Files.writeString(directory.resolve("chunks-import.json"), result.andReturn().getResponse().getContentAsString());
        assertNoCommit(f);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission_payload WHERE submission_id=?", Integer.class,
            Ids.bytes(UUID.fromString(f.id())))).isZero();
        restore(f, original);
        var receipt = f.pda.data(post(base + "/finalize").header("X-Server-Recovery-Epoch", "1"), f.tokens);
        assertThat(receipt.path("status").asText()).isEqualTo("SUCCEEDED");
        replacePurpose(f, SubmissionJson.object().put("scope", "pda:handover-import"));
        assertThat(f.pda.data(post(base + "/finalize").header("X-Server-Recovery-Epoch", "1"), f.tokens)).isEqualTo(receipt);
        assertThat(f.count("dispatch_record")).isEqualTo(2);
    }

    private void reviewCannotPromoteImportPurpose() throws Exception {
        var f = fixture(true);
        assertThat(f.submit().path("status").asText()).isEqualTo("REVIEW_REQUIRED");
        String original = replacePurpose(f, SubmissionJson.object().put("scope", "pda:handover-import"));
        int held = f.count("bale_commitment");
        var command = SubmissionJson.object().put("decision", "APPROVE").put("reason", "核验用途拒绝")
            .put("payloadSha256", SubmissionJson.sha(f.body));
        var result = f.web.send(post("/api/v1/submissions/" + f.id() + "/review")
            .contentType("application/json").content(command.toString())).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("OFFLINE_AUTH_SCOPE_INVALID"));
        Files.writeString(directory.resolve("review-import.json"), result.andReturn().getResponse().getContentAsString());
        assertThat(f.count("dispatch_record")).isZero();
        assertThat(f.count("inventory_ledger")).isZero();
        assertThat(f.count("bale_commitment")).isEqualTo(held);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission_review WHERE submission_id=?", Integer.class,
            Ids.bytes(UUID.fromString(f.id())))).isZero();
        restore(f, original);
        var receipt = f.review("APPROVE");
        assertThat(receipt.path("status").asText()).isEqualTo("SUCCEEDED");
        replacePurpose(f, SubmissionJson.object().put("scope", "pda:handover-import"));
        assertThat(f.review("APPROVE")).isEqualTo(receipt);
        assertThat(f.count("dispatch_record")).isEqualTo(2);
    }

    private SubmissionFixture fixture(boolean shortPlan) throws Exception {
        var identity = IdentityFixture.create(jdbc, app.getBean(PasswordEncoder.class), "WAREHOUSE_ADMIN");
        var web = new PublicationDriver(mvc, app.getBean(ObjectMapper.class), identity);
        return new SubmissionFixture(web, jdbc, true, shortPlan);
    }

    private String replacePurpose(SubmissionFixture f, ObjectNode fields) {
        String original = f.assignment.path("offlineToken").asText();
        var claims = (ObjectNode) SubmissionJson.parse(Base64.getUrlDecoder().decode(original.split("\\.")[1]));
        claims.remove("scope");
        claims.setAll(fields);
        restore(f, app.getBean(OfflineSigner.class).sign(claims).token());
        return original;
    }

    private void restore(SubmissionFixture f, String token) {
        assertThat(jdbc.update("UPDATE offline_authorization SET signed_token=? WHERE id=?", token,
            Ids.bytes(UUID.fromString(f.assignment.path("offlineAuthorizationId").asText())))).isEqualTo(1);
    }

    private void assertNoCommit(SubmissionFixture f) {
        assertThat(f.count("dispatch_record")).isZero();
        assertThat(f.count("inventory_ledger")).isZero();
        assertThat(f.count("bale_commitment")).isZero();
    }
}
