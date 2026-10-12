package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Signed exclusions feed actual linked/unlinked confirmation while changing no original evidence. */
final class TemporaryExclusionSelectionChecks {
    private final WebApplicationContext context;
    TemporaryExclusionSelectionChecks(WebApplicationContext context) { this.context=context; }
    void verify(Path attempt) throws Exception { linked(attempt);unlinked();newCandidate();reactivated(); }
    private void linked(Path attempt) throws Exception {
        var f=new TemporaryExclusionHttpFixture(context);String excluded=f.cancelled();
        var vectors=SubmissionJson.object().putArray("cases");
        var facts=new TemporaryLinkedFacts(f.linked);
        var before=facts.before();
        var exclusion=f.command(excluded,"CANCELLED");byte[] first=f.http.post(exclusion);
        vectors.add(vector(f,exclusion,before));before=facts.before();
        var link=f.source.command("LINK",f.supervisor).put("reason","主管完整核验排除原件后人工关联");
        var original=f.http.post(link);var linked=SubmissionJson.parse(original);
        vectors.add(vector(f,link,before));
        assertThat(linked.at("/businessDecision/candidates/total").asText()).isEqualTo("2");
        assertThat(linked.at("/businessDecision/selection/effectiveTotal").asText()).isEqualTo("1");
        assertThat(linked.at("/businessDecision/selection/exclusions/0/receiptId").asText())
            .isEqualTo(SubmissionJson.parse(first).path("receiptId").asText());
        assertThat(f.http.send(f.source.command("NORMAL_CONFIRM",f.supervisor)).statusCode()).isEqualTo(409);
        // Applying LINK changes the complete fingerprint, so the old exclusion cannot authorize confirm.
        before=facts.before();var recheck=f.command(excluded,"CANCELLED");f.http.post(recheck);vectors.add(vector(f,recheck,before));
        before=facts.before();
        var confirm=f.source.command("NORMAL_CONFIRM",f.supervisor);byte[] success=f.http.post(confirm);
        vectors.add(vector(f,confirm,before));
        assertThat(SubmissionJson.parse(success).at("/businessDecision/selection/effectiveTotal").asText()).isEqualTo("1");
        assertThat(f.http.post(link)).containsExactly(original);assertThat(f.http.post(confirm)).containsExactly(success);
        assertThat(f.http.original(f.originalPath(exclusion))).containsExactly(first);
        assertThat(f.source.jdbc.queryForObject("SELECT BIN_TO_UUID(order_id) FROM order_result_version WHERE submission_id=UUID_TO_BIN(?)",String.class,f.source.source.id()))
            .isEqualTo(f.linked.target);
        save(attempt,f,new ObjectNode[]{exclusion,link,recheck,confirm});
        Files.writeString(attempt.resolve("exclusion-selection-vectors.json"),SubmissionJson.canonical(SubmissionJson.object().set("cases",vectors)));
    }
    private ObjectNode vector(TemporaryExclusionHttpFixture f,ObjectNode command,ObjectNode before) throws Exception {
        var value=SubmissionJson.object().put("commandUtf8",SubmissionJson.canonical(command))
            .put("responseUtf8",new String(f.http.original(f.originalPath(command)),java.nio.charset.StandardCharsets.UTF_8))
            .put("payloadUtf8",SubmissionJson.canonical(f.source.source.body));
        value.set("before",before);value.set("record",new TemporaryLinkedFacts(f.linked).record(command.path("commandId").asText()));return value;
    }
    private void save(Path attempt,TemporaryExclusionHttpFixture f,ObjectNode[] commands) throws Exception {
        var input=SubmissionJson.object().put("username",f.supervisor.getUsername());
        var originals=input.putArray("originals");
        int index=0;
        for(var command:commands) {
            byte[] bytes=f.http.original(f.originalPath(command));String filename="selection-"+(index++)+"-"+command.path("commandType").asText()+".json";
            Files.write(attempt.resolve(filename),bytes);originals.addObject().put("path",f.originalPath(command)).put("file",filename);
        }
        Files.writeString(attempt.resolve("selection-restart-input.json"),SubmissionJson.canonical(input));
    }
    private void unlinked() throws Exception {
        var f=new TemporaryExclusionHttpFixture(context);
        f.source.jdbc.update("UPDATE outbound_order SET status='CANCELLED' WHERE id=UUID_TO_BIN(?)",f.linked.target);
        f.http.post(f.command(f.linked.target,"CANCELLED"));
        var receipt=SubmissionJson.parse(f.http.post(f.source.command("NORMAL_CONFIRM",f.supervisor)));
        assertThat(receipt.at("/businessDecision/candidates/total").asText()).isEqualTo("1");
        assertThat(receipt.at("/businessDecision/selection/effectiveTotal").asText()).isEqualTo("0");
        assertThat(receipt.at("/businessDecision/submissionReceipt/orderId").asText()).isEqualTo(f.linked.originalOrder);
    }
    private void newCandidate() throws Exception {
        var f=new TemporaryExclusionHttpFixture(context);f.http.post(f.command(f.cancelled(),"CANCELLED"));f.linked.createTarget();
        var before=f.state();assertThat(f.http.send(f.source.command("LINK",f.supervisor)).statusCode()).isEqualTo(409);
        assertThat(f.state()).isEqualTo(before);
    }
    private void reactivated() throws Exception {
        var f=new TemporaryExclusionHttpFixture(context);String id=f.cancelled();f.http.post(f.command(id,"CANCELLED"));
        f.source.jdbc.update("UPDATE outbound_order SET status='READY',row_version=row_version+1 WHERE id=UUID_TO_BIN(?)",id);
        var before=f.state();assertThat(f.http.send(f.source.command("LINK",f.supervisor)).statusCode()).isEqualTo(409);
        assertThat(f.state()).isEqualTo(before);
    }
}
