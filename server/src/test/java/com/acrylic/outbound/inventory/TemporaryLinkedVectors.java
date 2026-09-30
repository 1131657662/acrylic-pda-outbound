package com.acrylic.outbound.inventory;

import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP command/replay/original/detail vectors, backed by separate raw DB snapshots. */
public final class TemporaryLinkedVectors {
    private final WebApplicationContext context;
    public TemporaryLinkedVectors(WebApplicationContext context) { this.context = context; }

    public void write(Path attempt) throws Exception {
        var fixture = new TemporaryLinkedFixture(context);
        var link = execute(fixture, "LINK");
        var confirm = execute(fixture, "NORMAL_CONFIRM");
        assertThat(confirm.at("/facts/scope/mapping_version").asText()).isEqualTo("2");
        assertThat(confirm.at("/after/results").size()).isEqualTo(1);
        assertThat(confirm.at("/after/results/0/order_id").asText()).isEqualTo(fixture.target);
        var bundle = SubmissionJson.object();
        var key = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(context.getBean(OfflineSigner.class).publicKey());
        bundle.putObject("keys").set(key.path("keyId").asText(), key);
        bundle.putObject("cases").set("link", link);
        ((ObjectNode) bundle.path("cases")).set("confirm", confirm);
        Files.writeString(attempt.resolve("linked-decision-vectors.json"), SubmissionJson.canonical(bundle));
        Files.writeString(attempt.resolve("real-records.tsv"), "submission\toriginalOrder\tlinkedOrder\tlinkReceipt\tconfirmReceipt\n"
            + fixture.source.source.id() + "\t" + fixture.originalOrder + "\t" + fixture.target + "\t"
            + link.at("/record/id").asText() + "\t" + confirm.at("/record/id").asText() + "\n");
    }

    private ObjectNode execute(TemporaryLinkedFixture fixture, String kind) throws Exception {
        var f = fixture.source;
        var command = f.command(kind, f.operator());
        if (kind.equals("LINK")) command.put("reason", "人工逐项核对正式单计划后确认受控关联");
        var facts = new TemporaryLinkedFacts(fixture);
        var vector = SubmissionJson.object().put("payloadUtf8", SubmissionJson.canonical(f.source.body))
            .put("commandUtf8", SubmissionJson.canonical(command));
        vector.set("facts", facts.before());
        vector.put("startedAt", Instant.now().toString());
        byte[] original = fixture.post(command);
        vector.put("finishedAt", Instant.now().toString());
        var after = facts.state();
        assertThat(fixture.post(command)).as("same command key replays original bytes").containsExactly(original);
        assertThat(facts.state()).as("same key adds no database effects").isEqualTo(after);
        vector.set("after", after);
        vector.set("afterReplay", facts.state());
        vector.set("record", facts.record(command.path("commandId").asText()));
        captureOriginal(fixture, command.path("commandId").asText(), original, vector);
        if (kind.equals("NORMAL_CONFIRM")) vector.put("successHttpUtf8", fixture.success());
        return vector;
    }

    private void captureOriginal(TemporaryLinkedFixture fixture, String commandId, byte[] original, ObjectNode vector) throws Exception {
        var http = new TemporaryCandidateHttp();
        http.login(fixture.source.operator().getUsername());
        String path = "/temporary-command-receipts/" + commandId;
        String query = "?warehouseId=" + fixture.source.source.f.web.identity().warehouse();
        assertThat(http.original(path + "/original" + query)).as("HTTP original bytes").containsExactly(original);
        var detail = http.get(path + query);
        vector.put("responseUtf8", new String(original, StandardCharsets.UTF_8));
        vector.set("detail", detail);
    }
}
