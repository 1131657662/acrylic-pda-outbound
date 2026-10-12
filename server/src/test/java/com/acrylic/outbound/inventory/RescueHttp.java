package com.acrylic.outbound.inventory;

import com.acrylic.outbound.exports.ExportModels;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import org.springframework.web.context.WebApplicationContext;

final class RescueHttp {
    private static final String BASE="http://127.0.0.1:19434/api/v1";
    final RescueFixture f;final CorrectionExportHttpClient supervisor;final JsonNode tokens;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    RescueHttp(WebApplicationContext context,RescueFixture fixture) throws Exception {
        f=fixture;supervisor=CorrectionExportHttpClient.login(context,new ExportModels.Scope(f.supervisor.identity().user().id(),
            f.operator.identity().warehouse(),UUID.randomUUID()));
        tokens=data(post("/pda/auth/login",f.pda.loginBody(f.device),null),200);
    }
    HttpResponse<byte[]> post(String path,JsonNode body,UUID key) throws Exception {
        var request=request(path).header("Content-Type","application/json").header("X-Server-Recovery-Epoch","1");
        if(key!=null) request.header("Idempotency-Key",key.toString());
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    HttpResponse<byte[]> get(String path) throws Exception {
        return client.send(request(path).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    private HttpRequest.Builder request(String path) {
        var request=HttpRequest.newBuilder(URI.create(BASE+path)).timeout(Duration.ofSeconds(40));
        if(tokens!=null) request.header("Authorization","Bearer "+tokens.path("accessToken").asText());
        return request;
    }
    JsonNode data(HttpResponse<byte[]> response,int expected) { return CorrectionExportHttpClient.data(response,expected); }
    JsonNode create() throws Exception { return data(post("/pda/rescue-requests",f.signed(f.document),UUID.fromString(f.key)),200); }
    HttpResponse<byte[]> approve(String id,JsonNode body,UUID key) throws Exception {
        return supervisor.post("/rescue-requests/"+id+"/approve",body,key);
    }
    HttpResponse<byte[]> revoke(String id,JsonNode body,UUID key) throws Exception {
        return supervisor.post("/rescue-grants/"+id+"/revoke",body,key);
    }
}
