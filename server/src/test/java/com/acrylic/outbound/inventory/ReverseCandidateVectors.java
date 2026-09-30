package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual successful TEMP followed by a later formal order; HTTP queries do not change business facts. */
public final class ReverseCandidateVectors {
    private final WebApplicationContext context;
    public ReverseCandidateVectors(WebApplicationContext context) { this.context = context; }

    public void write(Path attempt) throws Exception {
        var source = new TemporaryConfirmationFixture(context, false);
        source.execute(source.command("NORMAL_CONFIRM", source.operator()), source.operator());
        assertThat(source.jdbc.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)",
            String.class, source.source.id())).isEqualTo("SUCCEEDED");
        var foreign = new TemporaryConfirmationFixture(context, false);
        var http = new TemporaryCandidateHttp(); http.login(source.operator().username());
        String warehouse = source.source.f.web.identity().warehouse().toString();
        var plans = new OrderTestDriver(source.source.f.web);
        var plan = plans.plan().put("vehicleNo", source.source.body.at("/temporaryOrder/vehicleNo").asText());
        String order = http.post("/orders?warehouseId=" + warehouse, plan, UUID.randomUUID().toString()).path("orderId").asText();
        var bundle = capture(source, http, order);
        var other = new TemporaryCandidateHttp(); other.login(foreign.operator().username());
        bundle.set("foreignDenied", other.error(path(warehouse, order), 404));
        assertThat(new ReverseCandidateFacts(source.jdbc).businessState()).isEqualTo(bundle.path("stateAfter"));
        Files.writeString(attempt.resolve("reverse-candidate-vectors.json"), SubmissionJson.canonical(bundle));
        String tab = String.valueOf((char) 9), newline = System.lineSeparator();
        Files.writeString(attempt.resolve("real-records.tsv"),
            String.join(tab, "successfulSubmission", "formalOrder", "warehouse") + newline
            + String.join(tab, source.source.id(), order, warehouse) + newline);
    }

    private ObjectNode capture(TemporaryConfirmationFixture source, TemporaryCandidateHttp http, String order) throws Exception {
        String warehouse = source.source.f.web.identity().warehouse().toString();
        var facts = new ReverseCandidateFacts(source.jdbc);
        var bundle = SubmissionJson.object().put("payloadUtf8", SubmissionJson.canonical(source.source.body));
        bundle.putObject("request").put("warehouseId", warehouse).put("orderId", order).put("page", 1);
        bundle.set("actor", new TemporaryDecisionFacts(source.jdbc).actor(source.operator().id()));
        bundle.set("facts", facts.read()); bundle.set("stateBefore", facts.businessState());
        var response = http.get(path(warehouse, order));
        assertThat(response.at("/candidates/total").asInt()).isEqualTo(1);
        assertThat(response.at("/candidates/items/0/submissionId").asText()).isEqualTo(source.source.id());
        assertThat(response.at("/candidates/items/0/status").asText()).isEqualTo("SUCCEEDED");
        assertThat(response.path("advisoryOnly").asBoolean()).isTrue();
        assertThat(response.path("releaseAllowed").asBoolean()).isFalse();
        bundle.set("response", response); bundle.set("stateAfter", facts.businessState());
        assertThat(bundle.path("stateAfter")).isEqualTo(bundle.path("stateBefore"));
        return bundle;
    }

    private String path(String warehouse, String order) {
        return "/orders/" + order + "/temporary-candidates?warehouseId=" + warehouse + "&page=1";
    }
}
