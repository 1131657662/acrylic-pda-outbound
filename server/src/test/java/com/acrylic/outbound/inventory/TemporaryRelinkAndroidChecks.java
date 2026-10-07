package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Duration;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Exchanges immutable checkpoints with Android across two actual controlled LINK commands. */
public final class TemporaryRelinkAndroidChecks {
    private final TemporaryLinkedFixture fixture;
    private final Path attempt;

    public TemporaryRelinkAndroidChecks(WebApplicationContext context,Path attempt) throws Exception {
        fixture=new TemporaryLinkedFixture(context);this.attempt=attempt;
    }

    public void verify() throws Exception {
        var first=command();byte[] original=fixture.post(first);
        save("first-link.json",SubmissionJson.parse(original));
        save("private/relink-input.json",identity());
        await("android-first.marker");
        String next=fixture.createTarget();var second=command();byte[] updated=fixture.post(second);
        save("second-link.json",SubmissionJson.parse(updated));
        save("relink-ready.json",SubmissionJson.object().put("canonicalOrderId",next).put("mappingVersion","3"));
        await("android-completed.marker");
        assertThat(fixture.post(first)).containsExactly(original);
        assertThat(fixture.post(second)).containsExactly(updated);
        assertThat(fixture.detail(first.path("commandId").asText()).at("/currentEffect/evaluation").asText())
            .isEqualTo("CONTEXT_CHANGED");
        var confirm=fixture.source.command("NORMAL_CONFIRM",fixture.source.operator());
        var rejected=fixture.send(confirm);
        assertThat(rejected.statusCode()).isEqualTo(409);
        assertThat(SubmissionJson.parse(rejected.body()).path("code").asText()).isEqualTo("TEMPORARY_DUPLICATES_UNRESOLVED");
        save("remaining-candidates-rejected.json",SubmissionJson.parse(rejected.body()));
        save("server-final.json",new TemporaryLinkedFacts(fixture).state());
    }

    private ObjectNode identity() {
        var source=fixture.source.source;
        String scope=fixture.source.jdbc.queryForObject("SELECT BIN_TO_UUID(scope_id) FROM submission WHERE id=UUID_TO_BIN(?)",
            String.class,source.id());
        return SubmissionJson.object().put("baseUrl","http://127.0.0.1:19434/api/v1/")
            .put("token",source.f.tokens.path("accessToken").asText()).put("scopeId",scope)
            .put("clientOrderId",source.a.client).put("canonicalOrderId",fixture.target)
            .put("userId",fixture.source.operator().id().toString()).put("deviceId",source.a.device())
            .put("warehouseId",source.f.web.identity().warehouse().toString()).put("serverRecoveryEpoch","1")
            .put("userAuthVersion","1").put("deviceVersion","1").put("localSessionId","isolated-relink");
    }

    private ObjectNode command() {
        return fixture.source.command("LINK",fixture.source.operator()).put("reason","隔离人工核对完整候选后关联");
    }
    private void save(String name,com.fasterxml.jackson.databind.JsonNode json) throws Exception {
        Files.writeString(attempt.resolve(name),SubmissionJson.canonical(json),StandardOpenOption.CREATE_NEW);
    }
    private void await(String marker) throws Exception {
        long deadline=System.nanoTime()+Duration.ofMinutes(10).toNanos();
        while(!Files.exists(attempt.resolve(marker))) {
            assertThat(Files.exists(attempt.resolve("android-failed.marker"))).isFalse();
            assertThat(System.nanoTime()).as("Android checkpoint "+marker).isLessThan(deadline);
            Thread.sleep(200);
        }
    }
}
