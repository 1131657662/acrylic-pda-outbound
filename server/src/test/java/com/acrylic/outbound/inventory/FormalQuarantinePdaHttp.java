package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

/** Isolated synthetic registered identity; actual bearer HTTP, never logs the bearer. */
final class FormalQuarantinePdaHttp {
    private final HttpClient client=HttpClient.newHttpClient();
    private final String bearer;
    private final Path log;
    FormalQuarantinePdaHttp(JsonNode tokens,Path log) { bearer=tokens.path("accessToken").asText(); this.log=log; }
    JsonNode post(String path,JsonNode body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda"+path)).timeout(Duration.ofSeconds(60))
            .header("Authorization","Bearer "+bearer).header("X-Server-Recovery-Epoch","1").header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))).build();
        var response=client.send(request,HttpResponse.BodyHandlers.ofByteArray());
        Files.writeString(log,"POST "+path+" "+response.statusCode()+" "+CorrectionAttachmentHttp.sha(response.body())+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        assertThat(response.statusCode()).as("%s %s",path,new String(response.body(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(200);
        return SubmissionJson.parse(response.body()).path("data");
    }
}
