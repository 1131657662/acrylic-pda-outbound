package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/** Preserves real cookie/CSRF reads from one isolated program process. */
final class E2aProgramReadHttp {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
        .proxy(new ProxySelector() {
            public java.util.List<Proxy> select(URI uri) { return java.util.List.of(Proxy.NO_PROXY); }
            public void connectFailed(URI uri, SocketAddress address, java.io.IOException error) { }
        }).build();
    private final Path output;
    private final URI base = URI.create("http://127.0.0.1:" + E2aTestPorts.http() + "/api/v1");

    E2aProgramReadHttp(Path output) throws Exception { this.output = Files.createDirectory(output); }

    ObjectNode read(Map<String, String> seed, E2aUpgradeRollbackProgram.Reader reader) throws Exception {
        String order = "/orders/" + seed.get("orderId");
        String suffix = "?warehouseId=" + seed.get("warehouseId");
        get("unauthenticated", order + suffix, 401);
        login(reader);
        var data = JSON.createObjectNode();
        for (var route : Map.of("orders", "/orders", "detail", order, "revisions", order + "/revisions",
                "publications", order + "/publications", "exports", order + "/exports").entrySet())
            data.set(route.getKey(), get(route.getKey(), route.getValue() + suffix, 200).path("data"));
        assertThat(data.path("orders").toString()).contains(seed.get("orderId"));
        assertThat(data.path("detail").toString()).contains(seed.get("orderId"));
        assertThat(data.path("revisions").isEmpty()).isFalse();
        assertThat(data.path("publications").isEmpty()).isFalse();
        Files.writeString(output.resolve("business.json"), data.toPrettyString(), StandardOpenOption.CREATE_NEW);
        return data;
    }

    private void login(E2aUpgradeRollbackProgram.Reader reader) throws Exception {
        var csrf = get("csrf", "/auth/csrf", 200).path("data");
        String body = "username=" + encode(reader.username()) + "&password=" + encode(reader.password());
        var request = HttpRequest.newBuilder(URI.create(base + "/auth/web/login"))
            .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/x-www-form-urlencoded")
            .header(csrf.path("headerName").asText(), csrf.path("token").asText())
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        assertThat(send("login", request, 200).path("data").path("authenticated").asBoolean()).isTrue();
    }

    private JsonNode get(String name, String path, int expected) throws Exception {
        return send(name, HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20)).GET().build(), expected);
    }

    private JsonNode send(String name, HttpRequest request, int expected) throws Exception {
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        Files.write(output.resolve(name + ".body.json"), response.body(), StandardOpenOption.CREATE_NEW);
        var facts = JSON.createObjectNode().put("url", request.uri().toString()).put("method", request.method())
            .put("status", response.statusCode()).put("expected", expected)
            .put("sha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(response.body())));
        facts.set("responseHeaders", JSON.valueToTree(response.headers().map()));
        Files.writeString(output.resolve(name + ".http.json"), facts.toPrettyString(), StandardOpenOption.CREATE_NEW);
        assertThat(response.statusCode()).as(request.uri().toString()).isEqualTo(expected);
        var parsed = JSON.readTree(response.body());
        if (expected == 200) {
            assertThat(parsed.path("code").asText()).isEqualTo("OK");
            assertThat(parsed.path("serviceMode").asText()).isNotEqualTo("UNAVAILABLE");
        }
        return parsed;
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
