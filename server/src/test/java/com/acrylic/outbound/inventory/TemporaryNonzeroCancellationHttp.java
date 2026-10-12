package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

/** Isolated native-source consumer; retain original HTTP bytes, never copy a PDA session. */
final class TemporaryNonzeroCancellationHttp {
    private final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
    private final Path output;
    private int sequence;
    TemporaryNonzeroCancellationHttp(Path output) throws Exception {this.output=Files.createDirectory(output);}

    void login(JsonNode account) throws Exception {
        var csrf=SubmissionJson.parse(get("/auth/csrf",200)).path("data");
        var request=builder("/auth/web/login").header(csrf.path("headerName").asText(),csrf.path("token").asText())
            .header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("username="+URLEncoder.encode(account.path("username").asText(),StandardCharsets.UTF_8)
                +"&password="+URLEncoder.encode(account.path("password").asText(),StandardCharsets.UTF_8)));
        require(client.send(request.build(),HttpResponse.BodyHandlers.ofByteArray()),200);
    }

    byte[] get(String path,int status) throws Exception {
        return require(client.send(builder(path).GET().build(),HttpResponse.BodyHandlers.ofByteArray()),status);
    }

    byte[] command(JsonNode command,int status) throws Exception {
        var csrf=SubmissionJson.parse(get("/auth/csrf",200)).path("data");
        byte[] bytes=SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8);
        String name=String.format("%03d",++sequence);Files.write(output.resolve(name+"-request.json"),bytes,StandardOpenOption.CREATE_NEW);
        var request=builder("/temporary-commands").header(csrf.path("headerName").asText(),csrf.path("token").asText())
            .header("Content-Type","application/json").header("X-Server-Recovery-Epoch","1")
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
        var response=client.send(request.build(),HttpResponse.BodyHandlers.ofByteArray());
        Files.write(output.resolve(name+"-response.json"),response.body(),StandardOpenOption.CREATE_NEW);
        Files.writeString(output.resolve(name+"-status.txt"),Integer.toString(response.statusCode()));return require(response,status);
    }

    private static byte[] require(HttpResponse<byte[]> response,int status) {
        assertThat(response.statusCode()).as(new String(response.body(),StandardCharsets.UTF_8)).isEqualTo(status);return response.body();
    }
    private static HttpRequest.Builder builder(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:19435/api/v1"+path)).timeout(Duration.ofSeconds(30));
    }
}
