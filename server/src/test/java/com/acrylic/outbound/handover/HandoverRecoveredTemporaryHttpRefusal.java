package com.acrylic.outbound.handover;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.Base64;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Real Web login and CSRF over localhost HTTP; ordinary commands retain the recovery rejection. */
final class HandoverRecoveredTemporaryHttpRefusal {
    private final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();

    static void verify(HandoverRecoveredTemporaryFixture f,JsonNode command) throws Exception {
        var http=new HandoverRecoveredTemporaryHttpRefusal(); var csrf=http.csrf();
        var login=http.send(request("/auth/web/login").header("Content-Type","application/x-www-form-urlencoded")
            .header(text(csrf,"headerName"),text(csrf,"token")).POST(HttpRequest.BodyPublishers.ofString(
                "username="+URLEncoder.encode(f.requester.username(),StandardCharsets.UTF_8)+"&password=Test-password-123")));
        assertThat(login.statusCode()).isEqualTo(200); csrf=http.csrf(); var before=f.snapshots.capture();
        var response=http.send(request("/handover-commands").header("Content-Type","application/json")
            .header(text(csrf,"headerName"),text(csrf,"token")).header("X-Server-Recovery-Epoch",text(command,"transportRecoveryEpoch"))
            .POST(HttpRequest.BodyPublishers.ofByteArray(HandoverReceiptCodec.bytes(command))));
        Files.write(f.directory.resolve("ordinary-http-response.json"),response.body());
        Files.writeString(f.directory.resolve("ordinary-http-result.json"),object().put("status",response.statusCode())
            .put("path","/api/v1/handover-commands").put("authenticated",true).put("csrf",true).put("epoch",text(command,"transportRecoveryEpoch")).toPrettyString());
        assertThat(response.statusCode()).isEqualTo(503); assertThat(text(parse(response.body()),"code")).isEqualTo("SERVER_RECOVERY_REQUIRED");
        var after=f.snapshots.capture();
        Files.writeString(f.directory.resolve("ordinary-http-before.json"),before.toPrettyString());
        Files.writeString(f.directory.resolve("ordinary-http-after.json"),after.toPrettyString());
        assertThat(withOriginalSessionTimes(before,after)).isEqualTo(before);
    }

    private static JsonNode withOriginalSessionTimes(JsonNode before,JsonNode after) {
        var result=after.deepCopy(); var original=before.path("tables").path("spring_session");
        var current=result.path("tables").path("spring_session"); assertThat(current.path("columns")).isEqualTo(original.path("columns"));
        int access=index(original,"LAST_ACCESS_TIME"),expiry=index(original,"EXPIRY_TIME");
        assertThat(current.path("rows").size()).isEqualTo(original.path("rows").size());
        for(int row=0;row<original.path("rows").size();row++) {
            var old=original.path("rows").get(row); var now=(ArrayNode)current.path("rows").get(row);
            assertThat(millis(now.get(access))).isGreaterThanOrEqualTo(millis(old.get(access)));
            assertThat(millis(now.get(expiry))-millis(now.get(access))).isEqualTo(millis(old.get(expiry))-millis(old.get(access)));
            now.set(access,old.get(access)); now.set(expiry,old.get(expiry));
        }
        return result;
    }
    private static int index(JsonNode table,String name) {
        for(int column=0;column<table.path("columns").size();column++) if(table.path("columns").get(column).asText().equals(name)) return column;
        throw new AssertionError("Session column missing: "+name);
    }
    private static long millis(JsonNode value) { return Long.parseLong(new String(Base64.getDecoder().decode(value.asText()),StandardCharsets.UTF_8)); }

    private JsonNode csrf() throws Exception {
        var response=send(request("/auth/csrf").GET()); assertThat(response.statusCode()).isEqualTo(200); return parse(response.body()).path("data");
    }
    private HttpResponse<byte[]> send(HttpRequest.Builder request) throws Exception { return client.send(request.build(),HttpResponse.BodyHandlers.ofByteArray()); }
    private static HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1"+path)).timeout(Duration.ofSeconds(30));
    }
}
