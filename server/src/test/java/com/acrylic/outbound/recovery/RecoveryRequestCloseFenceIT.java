package com.acrylic.outbound.recovery;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.*;

/** New isolated JDBC/HTTP/P5 composition; not full application or original recovery authority. */
class RecoveryRequestCloseFenceIT {
    static Stream<String> faults() {
        return Stream.of("close", "rollback").flatMap(method ->
            Stream.of("sql", "runtime", "error").map(type -> method + ":" + type));
    }

    @ParameterizedTest @MethodSource("faults")
    void unknownCompletionHaltsBeforeTheRequestCanBorrowAgain(String fault) throws Exception {
        EffectRealEnvironment.requireEnvironment();
        Path parent = Path.of(System.getenv("B11_EFFECT_EVIDENCE_DIR"), "private");
        Files.createDirectories(parent);
        String table = "hc_" + UUID.randomUUID().toString().replace("-", "");
        createTable(table);
        try (var client = HttpClient.newHttpClient(); var fixture = new HttpCloseFenceProcess(parent)) {
            fixture.launch(fault, table, "owner");
            URI base = URI.create("http://127.0.0.1:" + fixture.port());
            ordinaryRequests(client, base, fixture);
            assertThat(rows(table)).isEqualTo("1,2");
            var pending = client.sendAsync(request(base.resolve("/fault")), HttpResponse.BodyHandlers.ofString());
            prepared(fixture, table);
            Files.writeString(fixture.home.resolve("go"), "release", StandardOpenOption.CREATE_NEW);
            assertThat(fixture.exit(fixture.process, "owner")).isEqualTo(70);
            finishHttp(pending, fixture.home);
            fixture.assertUnknown();
            assertNoContinuation(fixture, table, fault);
            successorRefused(fixture, table);
            Files.writeString(fixture.home.resolve("result.json"), "{\"fault\":\"" + fault
                + "\",\"rows\":\"" + rows(table) + "\",\"exit\":70,\"successorRefused\":true}", StandardOpenOption.CREATE_NEW);
        }
    }

    private static void ordinaryRequests(HttpClient client, URI base, HttpCloseFenceProcess fixture) throws Exception {
        for (int id = 1; id <= 2; id++) {
            var response = client.send(request(base.resolve("/normal")), HttpResponse.BodyHandlers.ofString());
            Files.writeString(fixture.home.resolve("normal-" + id + ".txt"), response.statusCode() + "\n" + response.body(),
                StandardOpenOption.CREATE_NEW);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("committed=" + id);
        }
        fixture.normalRootsDrained();
    }

    private static void prepared(HttpCloseFenceProcess fixture, String table) throws Exception {
        fixture.awaitFile("prepared.json");
        var ready = RecoveryControlFixture.JSON.readTree(Files.readAllBytes(fixture.home.resolve("prepared.json")));
        assertThat(ready.path("phase").asText()).isEqualTo("SERVING");
        assertThat(ready.path("roots").asInt()).isEqualTo(1);
        assertThat(ready.path("children").asInt()).isEqualTo(1);
        assertThat(ready.path("operation").asText()).isNotBlank();
        fixture.writerHeld();
        assertThat(rows(table)).as("third row is real but not yet committed").isEqualTo("1,2");
    }

    private static void assertNoContinuation(HttpCloseFenceProcess fixture, String table, String fault) throws Exception {
        assertThat(fixture.home.resolve("caught-close.marker")).doesNotExist();
        assertThat(fixture.home.resolve("continued-after-close.marker")).doesNotExist();
        assertThat(fixture.home.resolve("owner-forced-cleanup.marker")).doesNotExist();
        assertThat(rows(table)).isEqualTo("1,2");
        String driver = Files.readString(fixture.home.resolve("driver.tsv"));
        assertThat(driver).contains("driver-returned|" + fault.split(":")[0], "MODEL-acknowledgement-fault|" + fault);
        assertThat(driver.lines().filter(line -> line.startsWith("borrow|")).count()).isEqualTo(3);
    }

    private static void successorRefused(HttpCloseFenceProcess fixture, String table) throws Exception {
        String driver = Files.readString(fixture.home.resolve("driver.tsv"));
        Process successor = fixture.launch("successor", table, "successor");
        try {
            assertThat(fixture.exit(successor, "successor")).isEqualTo(1);
            assertThat(Files.readString(fixture.home.resolve("successor.stderr"))).contains("RECOVERY_LEASE_OUTSTANDING");
            assertThat(fixture.home.resolve("successor-started.marker")).doesNotExist();
            assertThat(Files.readString(fixture.home.resolve("driver.tsv"))).isEqualTo(driver);
        } finally {
            if (successor.isAlive()) { successor.destroyForcibly(); successor.waitFor(15, TimeUnit.SECONDS); }
        }
    }

    private static HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45)).POST(HttpRequest.BodyPublishers.noBody()).build();
    }

    private static void finishHttp(CompletableFuture<HttpResponse<String>> pending, Path home) throws Exception {
        try {
            var response = pending.get(15, TimeUnit.SECONDS);
            Files.writeString(home.resolve("fault-http.txt"), "status=" + response.statusCode() + "\n" + response.body(),
                StandardOpenOption.CREATE_NEW);
            fail("unknown completion returned an HTTP response");
        } catch (ExecutionException expected) {
            assertThat(expected.getCause()).isInstanceOf(java.io.IOException.class);
            Files.writeString(home.resolve("fault-http.txt"), expected.getCause().toString(), StandardOpenOption.CREATE_NEW);
        }
    }

    private static void createTable(String table) throws Exception {
        try (var connection = EffectRealEnvironment.open(false); var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE " + table + " (id INT PRIMARY KEY) ENGINE=InnoDB");
        }
    }

    private static String rows(String table) throws Exception {
        try (var connection = EffectRealEnvironment.open(false); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT COALESCE(GROUP_CONCAT(id ORDER BY id),'') FROM " + table)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }
}
