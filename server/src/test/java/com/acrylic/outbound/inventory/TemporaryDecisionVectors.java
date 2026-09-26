package com.acrylic.outbound.inventory;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Real isolated transactions + cookie/PDA HTTP originals; only preparation uses synthetic identities. */
public final class TemporaryDecisionVectors {
    private final WebApplicationContext context;
    public TemporaryDecisionVectors(WebApplicationContext context) { this.context=context; }

    public void write(Path attempt) throws Exception {
        var normal=new TemporaryConfirmationFixture(context,false);
        var shortSource=new TemporaryConfirmationFixture(context,true);
        var supervisor=shortSource.supervisor();
        var approval=execute(shortSource,shortSource.command("SHORT_APPROVE",supervisor),supervisor);
        var shortFacts=new TemporaryDecisionFacts(shortSource.jdbc);
        var currentApproval=shortFacts.actor(supervisor.id());
        var confirmed=execute(shortSource,shortSource.command("NORMAL_CONFIRM",shortSource.operator()),shortSource.operator());
        ((ObjectNode)confirmed.path("facts")).set("approvalActor",currentApproval);
        confirmed.set("approval",approval);
        var result=SubmissionJson.object();
        var key=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(context.getBean(OfflineSigner.class).publicKey());
        result.putObject("keys").set(key.path("keyId").asText(),key);
        var cases=result.putObject("cases");cases.set("normal",execute(normal,normal.command("NORMAL_CONFIRM",normal.operator()),normal.operator()));
        cases.set("shortApprove",approval);cases.set("shortConfirm",confirmed);
        var other=new TemporaryConfirmationFixture(context,true);var otherSupervisor=other.supervisor();
        cases.set("otherApprove",execute(other,other.command("SHORT_APPROVE",otherSupervisor),otherSupervisor));
        var nonzero=new TemporaryCandidateVectors(context);var read=nonzero.read();result.set("candidateRead",read);
        nonzero.unresolvedRemainsBlocked();var candidateSupervisor=nonzero.source.supervisor();
        cases.set("nonzeroApprove",execute(nonzero.source,nonzero.source.command("SHORT_APPROVE",candidateSupervisor),candidateSupervisor));
        nonzero.unresolvedRemainsBlocked();nonzero.staleCursor(read);
        Files.writeString(attempt.resolve("decision-vectors.json"),SubmissionJson.canonical(result));
    }

    private ObjectNode execute(TemporaryConfirmationFixture f,ObjectNode command,SessionUser actor) throws Exception {
        var facts=new TemporaryDecisionFacts(f.jdbc);var before=facts.before(f,actor.id());
        var vector=SubmissionJson.object().put("payloadUtf8",SubmissionJson.canonical(f.source.body))
            .put("commandUtf8",new String(TemporaryConfirmationFixture.bytes(command),StandardCharsets.UTF_8));
        vector.set("facts",before);vector.put("startedAt",Instant.now().toString());
        var bytes=f.execute(command,actor);vector.put("finishedAt",Instant.now().toString());
        assertThat(f.execute(command,actor)).as("original key replay").containsExactly(bytes);
        var http=new TemporaryCandidateHttp();http.login(actor.getUsername());
        String path="/temporary-command-receipts/"+command.path("commandId").asText();
        String query="?warehouseId="+f.source.f.web.identity().warehouse();
        assertThat(http.original(path+"/original"+query)).as("real HTTP original bytes").containsExactly(bytes);
        var detail=http.get(path+query);
        assertThat(detail.path("originalCommandReceiptUtf8").asText()).isEqualTo(new String(bytes,StandardCharsets.UTF_8));
        vector.put("responseUtf8",detail.path("originalCommandReceiptUtf8").asText());vector.set("detail",detail);
        vector.set("record",facts.record(command.path("commandId").asText()));
        if(command.path("commandType").asText().equals("NORMAL_CONFIRM"))vector.put("successHttpUtf8",success(f));
        return vector;
    }

    private String success(TemporaryConfirmationFixture f) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/submissions/"+f.source.id()))
            .header("Authorization","Bearer "+f.source.f.tokens.path("accessToken").asText()).GET().build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);return response.body();
    }
}
