package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

/** Real recipient HTTP, recording original commands and responses without transport credentials. */
final class HandoverTemporaryConfirmationHttp {
    final HandoverTemporaryTargetFixture f;
    final Path directory;
    private final AtomicInteger calls = new AtomicInteger();
    HandoverTemporaryConfirmationHttp(HandoverTemporaryTargetFixture fixture) throws Exception {
        f = fixture; directory = Files.createDirectory(f.directory.resolve("confirmation"));
    }

    byte[] submit(JsonNode body, int status, String code) throws Exception {
        return send(new Request(body, f.token, "1", status, code));
    }

    byte[] send(Request input) throws Exception {
        byte[] bytes = SubmissionJson.canonical(input.body()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/handovers/" + f.source.kase + "/temporary-confirmation"))
            .timeout(java.time.Duration.ofSeconds(30)).header("X-Server-Recovery-Epoch", input.epoch())
            .header("Authorization", "Bearer " + input.token()).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        int call = calls.incrementAndGet(); Files.write(directory.resolve("command-" + call + ".json"), bytes);
        Files.write(directory.resolve("response-" + call + ".bin"), response.body());
        Files.writeString(directory.resolve("http-" + call + ".txt"), request.method() + " " + request.uri().getPath() + " " + response.statusCode());
        assertThat(response.statusCode()).as(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(input.status());
        if (input.code() != null) assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo(input.code());
        if (input.status() == 200) assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        return response.body();
    }
    record Request(JsonNode body, String token, String epoch, int status, String code) { }
}
