package com.acrylic.outbound.inventory;

import com.acrylic.outbound.foundationmigration.FoundationEnvironment;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Only bridge to package-private legacy factories; all generated facts remain in the new lab. */
public final class FoundationLegacySeed {
    private final ApplicationContext context;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ObjectNode manifest;

    public FoundationLegacySeed(ApplicationContext context) {
        this.context = context; jdbc = context.getBean(JdbcTemplate.class); json = context.getBean(ObjectMapper.class);
        manifest = json.createObjectNode(); FoundationEnvironment.verifyConnected(jdbc);
    }

    public void generate() throws Exception {
        formal(); temporary(); recovery(); pc(); audit();
        Files.createDirectories(FoundationEnvironment.evidence("seed-manifest.json").getParent());
        Files.writeString(FoundationEnvironment.evidence("seed-manifest.json"), json.writeValueAsString(manifest));
    }

    private PublicationDriver web() {
        return new PublicationDriver(context.getBean(MockMvc.class), json,
                IdentityFixture.create(jdbc, context.getBean(PasswordEncoder.class), "WAREHOUSE_ADMIN"));
    }

    private SubmissionFixture formal(boolean deduct, boolean shortPlan) throws Exception {
        return new SubmissionFixture(web(), jdbc, deduct, shortPlan);
    }

    private void formal() throws Exception {
        for (boolean deduct : List.of(true, false)) {
            var f = formal(deduct, false); var receipt = f.submit();
            assertThat(receipt.path("status").asText()).isEqualTo("SUCCEEDED");
            remember("formal-success-" + deduct, f, f.body, receipt);
            var body = json.createObjectNode().put("payloadSha256", SubmissionJson.sha(f.body))
                    .put("receiptSha256", SubmissionJson.sha(receipt));
            f.pda.data(post("/api/v1/pda/submissions/" + f.id() + "/archive-confirmation")
                    .header("X-Server-Recovery-Epoch", "1").contentType("application/json").content(body.toString()), f.tokens);
        }
        var review = formal(false, true); remember("formal-review", review, review.body, review.submit());
        var rejected = formal(false, true); rejected.submit(); remember("formal-rejected", rejected, rejected.body, rejected.review("REJECT"));
        var aborted = formal(false, false); remember("formal-aborted", aborted, aborted.body, aborted.abort());
        var receiving = formal(false, false); begin(receiving, receiving.body); remember("formal-receiving", receiving, receiving.body, null);
        registered();
    }

    private void registered() throws Exception {
        var f = formal(false, false);
        var control = SubmissionControl.read(UUID.fromString(f.id()), f.body);
        var actor = new SubmissionAccess.Actor(UUID.fromString(f.tokens.path("userId").asText()),
                f.web.identity().warehouse(), "1");
        var tx = new TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        tx.executeWithoutResult(status -> context.getBean(SubmissionStore.class).lock(control, actor));
        remember("formal-registered-store-fixture", f, f.body, null);
    }

    private void temporary() throws Exception {
        var a = temp(false); remember("temp-a", a.f, a.body, a.abort());
        var b = temp(true); byte[] bytes = begin(b.f, b.body); chunk(b, 0, Arrays.copyOfRange(bytes, 0, 262144));
        remember("temp-b", b.f, b.body, b.abort());
        var all = temp(false); bytes = begin(all.f, all.body); chunk(all, 0, bytes);
        remember("temp-all-chunks", all.f, all.body, all.read());
        var c = temp(false); c.submit(); remember("temp-c", c.f, c.body, c.abort());
        var pending = temp(false); remember("temp-pending", pending.f, pending.body, pending.submit());
        var conflict = temp(false);
        jdbc.update("UPDATE inventory_position SET blocked_reason='Foundation synthetic conflict' "
                + "WHERE last_seen_snapshot_id=UUID_TO_BIN(?)", conflict.body.path("inventorySnapshotId").asText());
        remember("temp-conflict", conflict.f, conflict.body, conflict.submit());
    }

    private TemporarySubmissionFixture temp(boolean large) throws Exception {
        var f = formal(false, false);
        if (large) f.addAcknowledgedFailures(400);
        return new TemporarySubmissionFixture(f);
    }

    private byte[] begin(SubmissionFixture f, ObjectNode body) throws Exception {
        byte[] bytes = SubmissionJson.canonical(body).getBytes(StandardCharsets.UTF_8);
        var control = body.deepCopy().put("payloadSha256", SubmissionJson.sha(body))
                .put("byteCount", Integer.toString(bytes.length)).put("chunkCount", Integer.toString((bytes.length + 262143) / 262144));
        f.pda.data(post("/api/v1/pda/submissions/" + body.path("submissionId").asText() + "/upload")
                .header("X-Server-Recovery-Epoch", "1").contentType("application/json").content(control.toString()), f.tokens);
        return bytes;
    }

    private void chunk(TemporarySubmissionFixture t, int index, byte[] bytes) throws Exception {
        t.f.pda.data(post(t.base() + "/chunks/" + index).header("X-Server-Recovery-Epoch", "1")
                .contentType("application/octet-stream").content(bytes), t.f.tokens);
    }

    private void recovery() throws Exception {
        var recall = new RecallFixture(formal(false, false)); recall.start(false); recall.upload(); recall.stop();
        remember("formal-recovery", recall.f, recall.body, null);
        var continuation = new ContinuationFixture(formal(false, false)); continuation.request(); continuation.approve();
        remember("formal-continuation-reserved", continuation.r.f, continuation.r.body, null);
        var disposition = new DispositionFixture(new ContinuationFixture(formal(false, false)), "QUARANTINE");
        disposition.activate(); remember("formal-quarantine", disposition.c.r.f, disposition.c.r.body, null);
    }

    private void pc() throws Exception {
        var reserved = new PcFixture(web(), false, 1, 1); reserved.start(); reserved.add(0);
        manifest.put("pc-reserved", reserved.order);
        var dispatched = new PcFixture(web(), false, 1, 1); dispatched.start();
        for (int i = 0; i < 3; i++) dispatched.add(i);
        dispatched.submit(""); manifest.put("pc-dispatched", dispatched.order);
        var isolated = new PcExceptionFixture(web()); isolated.stop();
        isolated.propose(isolated.plan(isolated.pc.api.identity().user().username(), "RETAIN", "RELEASE", "QUARANTINE"));
        isolated.approve(); isolated.apply(); manifest.put("pc-quarantine", isolated.pc.order);
    }

    private void audit() throws Exception {
        var f = formal(false, false); var a = new TemporaryAuditFixture(f);
        var mixed = a.mixed(a.event(8)); a.send(mixed); var proof = a.archive(mixed);
        assertThat(proof.path("localDeletionAllowed").asBoolean()).isFalse();
        remember("mixed-audit", f, f.body, f.submit());
    }

    private void remember(String name, SubmissionFixture f, ObjectNode body, JsonNode receipt) {
        var entry = manifest.putObject(name); entry.set("body", body); entry.set("tokens", f.tokens);
        entry.put("order", f.order); if (receipt != null) entry.set("receipt", receipt);
    }

    public void assertStoredResponseDtos() throws Exception {
        var saved = json.readTree(FoundationEnvironment.evidence("seed-manifest.json").toFile());
        for (String name : List.of("formal-success-true", "formal-success-false", "formal-aborted", "temp-a", "temp-b", "temp-c", "temp-pending")) {
            var entry = saved.path(name); var id = UUID.fromString(entry.path("body").path("submissionId").asText());
            var head = context.getBean(SubmissionStore.class).required(id);
            var response = context.getBean(SubmissionService.class).response(head);
            assertThat(response).as(name + " service response only; no authentication bypass request").isEqualTo(entry.path("receipt"));
        }
    }
}
