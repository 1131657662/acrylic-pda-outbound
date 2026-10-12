package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Real session/CSRF traffic; records methods, paths, response statuses and hashes without session secrets. */
final class CorrectionAttachmentHttp {
    private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    private final Path log;

    CorrectionAttachmentHttp(String username, Path log) throws Exception {
        this.log = log;
        var token = csrf();
        send(request("/auth/web/login").header(token.path("headerName").asText(), token.path("token").asText())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=Test-password-123")), 200);
    }

    JsonNode upload(String warehouse, String key, byte[] bytes, String hash, int status, boolean chunked) throws Exception {
        var token = csrf();
        var publisher = chunked ? HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes))
            : HttpRequest.BodyPublishers.ofByteArray(bytes);
        var response = send(request("/corrections/attachments/" + key + "?warehouseId=" + warehouse)
            .header(token.path("headerName").asText(), token.path("token").asText())
            .header("Content-Type", "application/octet-stream").header("X-Server-Recovery-Epoch", "1")
            .header("X-Content-SHA256", hash).PUT(publisher), status);
        return SubmissionJson.parse(response.body());
    }

    byte[] read(String warehouse, String attachment, boolean review, int status) throws Exception {
        var response = send(request("/corrections/attachments/" + attachment + (review ? "/review" : "")
            + "?warehouseId=" + warehouse).GET(), status);
        if (status == 200) {
            assertThat(response.headers().firstValue("Content-Type").orElse("")).isEqualTo("application/octet-stream");
            assertThat(response.headers().firstValue("Content-Disposition").orElse("")).contains("attachment; filename=");
        }
        return response.body();
    }

    byte[] command(JsonNode body, int status) throws Exception {
        var token = csrf();
        return send(request("/corrections").header(token.path("headerName").asText(), token.path("token").asText())
            .header("Content-Type", "application/json").header("X-Server-Recovery-Epoch", "1")
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))), status).body();
    }

    byte[] get(String path, int status) throws Exception { return send(request(path).GET(), status).body(); }
    JsonNode post(String path,String key,JsonNode body,int status) throws Exception {
        var token=csrf();
        return SubmissionJson.parse(send(request(path).header(token.path("headerName").asText(),token.path("token").asText())
            .header("Idempotency-Key",key).header("X-Server-Recovery-Epoch","1").header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))),status).body()).path("data");
    }

    private JsonNode csrf() throws Exception { return SubmissionJson.parse(send(request("/auth/csrf").GET(), 200).body()).path("data"); }

    private HttpResponse<byte[]> send(HttpRequest.Builder builder, int expected) throws Exception {
        var request = builder.build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        Files.writeString(log, request.method() + " " + request.uri().getPath() + " " + response.statusCode()
            + " " + sha(response.body()) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        assertThat(response.statusCode()).as("%s %s response=%s", request.method(), request.uri().getPath(),
            response.statusCode() >= 400 ? new String(response.body(), StandardCharsets.UTF_8) : "bytes").isEqualTo(expected);
        if (expected == 200 && request.uri().getPath().contains("/corrections")) {
            assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
            assertThat(response.headers().firstValue("X-Content-Type-Options").orElse("")).isEqualTo("nosniff");
        }
        return response;
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1" + path)).timeout(Duration.ofSeconds(60));
    }

    static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
