package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Normal/short successes with actual internal V2 archives; original GET remains real HTTP. */
public final class TemporaryArchiveRecoveryFixture {
    public record Original(String username,String token,String submission,String warehouse,String commandId,byte[] response,JsonNode proof) { }
    private final WebApplicationContext context;
    public TemporaryArchiveRecoveryFixture(WebApplicationContext context) { this.context=context; }

    public List<Original> create(Path attempt) throws Exception {
        var originals=List.of(create(false),create(true));var exported=SubmissionJson.object().putArray("archives");
        for(var item:originals)exported.add(item.proof());
        Files.writeString(attempt.resolve("archive-proofs.json"),SubmissionJson.canonical(exported));
        return originals;
    }

    private Original create(boolean shortShipment) throws Exception {
        var f=new TemporaryConfirmationFixture(context,shortShipment);
        if(shortShipment) { var supervisor=f.supervisor();f.execute(f.command("SHORT_APPROVE",supervisor),supervisor); }
        var command=f.command("NORMAL_CONFIRM",f.operator());byte[] response=f.execute(command,f.operator());
        var success=SubmissionJson.parse(response).at("/businessDecision/submissionReceipt");
        var request=SubmissionJson.object().put("payloadSha256",SubmissionJson.sha(f.source.body)).put("receiptSha256",SubmissionJson.sha(success));
        String token=f.source.f.tokens.path("accessToken").asText();
        var proof=new TemporaryArchiveDriver(context).archive(UUID.fromString(f.source.id()),token,request);
        var original=new Original(f.operator().getUsername(),token,f.source.id(),f.source.f.web.identity().warehouse().toString(),
            command.path("commandId").asText(),response,proof);
        check(original);return original;
    }

    public void check(Original original) throws Exception {
        var http=new TemporaryCandidateHttp();http.login(original.username());
        String path="/temporary-command-receipts/"+original.commandId();String query="?warehouseId="+original.warehouse();
        assertThat(http.original(path+"/original"+query)).containsExactly(original.response());
        assertThat(http.get(path+query).path("originalCommandReceiptUtf8").asText())
            .isEqualTo(new String(original.response(),StandardCharsets.UTF_8));
        var request=SubmissionJson.object();for(String key:List.of("payloadSha256","receiptSha256"))request.set(key,original.proof().get(key));
        assertThat(new TemporaryArchiveDriver(context).archive(UUID.fromString(original.submission()),original.token(),request)).isEqualTo(original.proof());
    }
}
