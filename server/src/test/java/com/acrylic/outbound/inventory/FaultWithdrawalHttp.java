package com.acrylic.outbound.inventory;

import com.acrylic.outbound.exports.ExportModels;
import com.acrylic.outbound.e2averify.E2aTestPorts;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

final class FaultWithdrawalHttp {
    static final String BASE="http://127.0.0.1:" + E2aTestPorts.http() + "/api/v1";
    final FaultRestartFixture f;
    final CorrectionExportHttpClient web;
    private final HttpClient pda=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    FaultWithdrawalHttp(WebApplicationContext context,FaultRestartFixture f) throws Exception {
        this.f=f;
        web=CorrectionExportHttpClient.login(context,new ExportModels.Scope(f.source.web.identity().user().id(),f.source.web.identity().warehouse(),UUID.fromString(f.source.order)));
    }
    HttpResponse<byte[]> withdraw(JsonNode body,UUID key) throws Exception { return web.post("/orders/"+f.source.order+"/change-requests",body,key); }
    HttpResponse<byte[]> submit(JsonNode body,JsonNode tokens) throws Exception { return post("/pda/orders/"+f.source.order+"/submissions",body,tokens,null); }
    HttpResponse<byte[]> post(String path,JsonNode body,JsonNode tokens,UUID key) throws Exception {
        var builder=request(path,tokens).header("Content-Type","application/json").header("X-Server-Recovery-Epoch","1");
        if(key!=null)builder.header("Idempotency-Key",key.toString());
        return pda.send(builder.POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    HttpResponse<byte[]> get(String path,JsonNode tokens) throws Exception { return pda.send(request(path,tokens).GET().build(),HttpResponse.BodyHandlers.ofByteArray()); }
    private HttpRequest.Builder request(String path,JsonNode tokens) {
        return HttpRequest.newBuilder(URI.create(BASE+path)).timeout(Duration.ofSeconds(40))
            .header("Authorization","Bearer "+tokens.path("accessToken").asText());
    }
    JsonNode data(HttpResponse<byte[]> response,int expected) { return CorrectionExportHttpClient.data(response,expected); }
    void claimAndDownload() throws Exception {
        var device=f.source.pda.register();
        var login=f.source.pda.loginBody(device);
        f.tokens=data(post("/pda/auth/login",login,SubmissionJson.object(),null),200);
        f.assignment=data(post("/pda/orders/"+f.source.order+"/assignments",f.source.pda.claimBody().put("expectedVersion",f.version()),f.tokens,UUID.randomUUID()),200);
        var manifest=data(post("/pda/assignments/"+f.assignment.path("id").asText()+"/preparation",SubmissionJson.object(),f.tokens,null),200);
        var unpacked=SubmissionJson.parse(manifest.path("manifest").asText().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for(var part:unpacked.path("parts")) {
            var response=get("/pda/assignments/"+f.assignment.path("id").asText()+"/content/"+part.path("name").asText()+"?bundleId="+manifest.path("bundleId").asText(),f.tokens);
            assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).isNotEmpty();
        }
        var ack=SubmissionJson.object().put("bundleId",manifest.path("bundleId").asText()).put("manifestSha256",manifest.path("manifestSha256").asText())
            .put("assignmentEpoch",f.assignment.path("epoch").asText()).put("inventorySnapshotId",f.assignment.path("inventorySnapshotId").asText()).put("activatedAt",Instant.now().toString());
        data(post("/pda/assignments/"+f.assignment.path("id").asText()+"/download-ack",ack,f.tokens,null),200);
    }
}
