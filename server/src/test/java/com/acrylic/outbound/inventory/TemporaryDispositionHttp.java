package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

/** Cookie/CSRF client retaining the exact command and response without printing credentials. */
final class TemporaryDispositionHttp {
    private final HttpClient client = HttpClient.newBuilder()
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    private final Path evidence;
    private int sequence;

    TemporaryDispositionHttp(Path evidence) throws Exception {
        this.evidence = Files.createDirectory(evidence);
    }

    void login(String username) throws Exception {
        var csrf = data("/auth/csrf");
        var request = builder("/auth/web/login").header(csrf.path("headerName").asText(), csrf.path("token").asText())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=Test-password-123"));
        require(client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray()), 200);
    }

    JsonNode data(String path) throws Exception {
        return SubmissionJson.parse(get(path)).path("data");
    }

    byte[] get(String path) throws Exception {
        return get(path, 200);
    }

    byte[] get(String path, int status) throws Exception {
        return require(client.send(builder(path).GET().build(), HttpResponse.BodyHandlers.ofByteArray()), status);
    }

    byte[] command(JsonNode command, int status) throws Exception {
        var csrf = data("/auth/csrf");
        String name = String.format("%03d-%s", ++sequence, command.path("commandType").asText());
        byte[] bytes = SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8);
        Files.write(evidence.resolve(name + "-request.json"), bytes, StandardOpenOption.CREATE_NEW);
        var request = builder("/temporary-commands").header(csrf.path("headerName").asText(), csrf.path("token").asText())
            .header("Content-Type", "application/json").header("X-Server-Recovery-Epoch", "1")
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        Files.write(evidence.resolve(name + "-response.json"), response.body(), StandardOpenOption.CREATE_NEW);
        Files.writeString(evidence.resolve(name + "-status.txt"), Integer.toString(response.statusCode()));
        return require(response, status);
    }

    private static byte[] require(HttpResponse<byte[]> response, int status) {
        assertThat(response.statusCode()).as("HTTP response: %s", new String(response.body(), StandardCharsets.UTF_8))
            .isEqualTo(status);
        return response.body();
    }

    private static HttpRequest.Builder builder(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1" + path)).timeout(Duration.ofSeconds(30));
    }
}
