package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.SubmissionHttpInput;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

/** Original TCP bytes for one synthetic FORMAL submission; credentials never enter evidence. */
final class SubmissionChunkFaultWire {
    final SubmissionHttpInput input;
    final Path evidence;
    private final int port;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final AtomicInteger sequence = new AtomicInteger();

    SubmissionChunkFaultWire(Path directory, int port, SubmissionHttpInput input) throws Exception {
        evidence = Files.createDirectory(directory); this.port = port; this.input = input;
        Files.write(evidence.resolve("frozen-body.bin"), input.payload(), StandardOpenOption.CREATE_NEW);
        var manifest = input.body().deepCopy().put("payloadSha256", SubmissionJson.sha(input.body()))
            .put("byteCount", Integer.toString(input.payload().length)).put("chunkCount", Integer.toString(count()));
        save("manifest", manifest);
    }

    int count() { return (input.payload().length + 262143) / 262144; }

    void upload() throws Exception {
        request("/upload", Files.readAllBytes(evidence.resolve("manifest.json")), 200);
        for (int index = count() - 1; index >= 0; index--) chunk(index, 200);
    }

    void start() throws Exception { request("/upload", Files.readAllBytes(evidence.resolve("manifest.json")), 200); }

    JsonNode chunk(int index, int expected) throws Exception {
        int from = index * 262144, to = Math.min(input.payload().length, from + 262144);
        return request("/chunks/" + index, Arrays.copyOfRange(input.payload(), from, to), expected);
    }

    JsonNode finish(int expected) throws Exception { return request("/finalize", new byte[0], expected); }

    JsonNode request(String suffix, byte[] bytes, int expected) throws Exception {
        String label = String.format("%03d", sequence.incrementAndGet());
        String path = "/api/v1/pda/submissions/" + input.submissionId() + suffix;
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(40)).header("Authorization", "Bearer " + input.accessToken())
            .header("X-Server-Recovery-Epoch", "1").header("X-Request-Id", "chunk-fault-" + label)
            .header("Content-Type", "application/octet-stream").POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        Files.write(evidence.resolve(label + "-request.bin"), bytes, StandardOpenOption.CREATE_NEW);
        Files.write(evidence.resolve(label + "-response.bin"), response.body(), StandardOpenOption.CREATE_NEW);
        save(label + "-http", SubmissionJson.object().put("path", path).put("status", response.statusCode())
            .put("requestSha256", com.acrylic.outbound.inventory.ImportFiles.sha256(bytes))
            .put("responseSha256", com.acrylic.outbound.inventory.ImportFiles.sha256(response.body())));
        assertThat(response.statusCode()).as(path + ": " + new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(expected);
        var parsed = SubmissionJson.parse(response.body());
        return parsed.hasNonNull("data") ? parsed.get("data") : parsed;
    }

    void save(String name, JsonNode value) throws Exception {
        Files.writeString(evidence.resolve(name + ".json"), value.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }
}
