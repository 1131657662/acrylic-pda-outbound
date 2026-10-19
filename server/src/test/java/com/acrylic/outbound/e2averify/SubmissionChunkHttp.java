package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.SubmissionHttpInput;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Records actual TCP chunk requests and responses for one isolated submission. */
final class SubmissionChunkHttp {
    private final int port;
    private final Path evidence;
    private final SubmissionHttpInput input;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private int sequence;

    SubmissionChunkHttp(int port, Path evidence, SubmissionHttpInput input) throws Exception {
        this.port = port; this.evidence = Files.createDirectories(evidence); this.input = input;
    }

    void run() throws Exception {
        var base = "/api/v1/pda/submissions/" + input.submissionId();
        var manifest = SubmissionJson.parse(input.body().toString()).deepCopy();
        int size = input.payload().length, chunkSize = 262144;
        int count = (size + chunkSize - 1) / chunkSize;
        ((com.fasterxml.jackson.databind.node.ObjectNode) manifest).put("payloadSha256", SubmissionJson.sha(input.body()))
            .put("byteCount", Integer.toString(size)).put("chunkCount", Integer.toString(count));
        request(base + "/upload", SubmissionJson.canonical(manifest).getBytes(java.nio.charset.StandardCharsets.UTF_8), 200);
        request(base + "/finalize", new byte[0], 400);
        for (int index = count - 1; index >= 0; index--) {
            int from = index * chunkSize, to = Math.min(size, from + chunkSize);
            request(base + "/chunks/" + index, Arrays.copyOfRange(input.payload(), from, to), 200);
            if (index == 0) request(base + "/chunks/0", Arrays.copyOfRange(input.payload(), 0, chunkSize), 200);
        }
        byte[] changed = input.payload().clone(); changed[0] ^= 1;
        request(base + "/chunks/0", Arrays.copyOfRange(changed, 0, chunkSize), 409);
        JsonNode receipt = request(base + "/finalize", new byte[0], 200);
        assertThat(receipt.path("data").path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(receipt.path("data").path("payloadSha256").asText()).isEqualTo(SubmissionJson.sha(input.body()));
    }

    private JsonNode request(String path, byte[] body, int expected) throws Exception {
        String label = String.format("%03d", ++sequence);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(40)).header("Authorization", "Bearer " + input.accessToken())
            .header("X-Server-Recovery-Epoch", "1").header("X-Request-Id", "submission-chunk-" + label)
            .header("Content-Type", path.endsWith("/finalize") ? "application/json" : "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        Files.write(evidence.resolve(label + "-request.bin"), body);
        Files.write(evidence.resolve(label + "-response.bin"), response.body());
        Files.writeString(evidence.resolve(label + "-status.txt"), Integer.toString(response.statusCode()));
        assertThat(response.statusCode()).as(path + ": " + new String(response.body())).isEqualTo(expected);
        return response.body().length == 0 ? SubmissionJson.object() : SubmissionJson.parse(response.body());
    }
}
