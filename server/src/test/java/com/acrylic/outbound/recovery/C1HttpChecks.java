package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Real loopback requests to a test-only controller over production C1 components. */
final class C1HttpChecks {
    private final C1MainProfile profile;
    private final InitialNormalStore store;
    private final byte[] original;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private int requests;

    C1HttpChecks(C1MainProfile profile) throws Exception {
        this.profile = profile;
        var value = InitialNormalFixture.request()
            .put("mysqlServerUuid", profile.jdbc.queryForObject("SELECT @@server_uuid", String.class))
            .put("databaseSchema", profile.jdbc.queryForObject("SELECT DATABASE()", String.class))
            .put("numericEpoch", profile.jdbc.queryForObject("SELECT recovery_epoch FROM server_state", String.class));
        original = RecoveryControlJson.encode(value);
        Path root = Files.createDirectory(profile.attempt.resolve("initial-authority"));
        Files.write(root.resolve("coordinator.lock"), new byte[]{'0'}, StandardOpenOption.CREATE_NEW);
        Path trusted = Files.write(profile.attempt.resolve("trusted-original.json"), original, StandardOpenOption.CREATE_NEW);
        store = new InitialNormalStore(new RecoveryControlLocation(root, InitialNormalFixture.INSTALLATION,
            List.of(profile.attempt.resolve("data"), profile.attempt.resolve("storage"))), trusted);
    }

    void missingSidesAndRegister() throws Exception {
        denied(); // Neither side exists.
        store.prepare(original); denied(); // Prepared original, no completed side.
        store.locked(() -> { store.completeLocked(InitialNormalEvidence.parse(original).sha256()); return null; });
        denied(); // Complete external witness alone never proves database commit.
        assertThat(call("register", true).statusCode()).isGreaterThanOrEqualTo(400);
        Files.move(profile.attempt.resolve("initial-authority").resolve(InitialNormalStore.COMPLETE),
            profile.attempt.resolve("test-unmatched-completion.json"));
        var registered = json(call("register", true));
        assertThat(registered.path("outcome").asText()).isEqualTo("REGISTERED");
        assertThat(registered.path("ordinaryWritesAllowed").asBoolean()).isFalse();
        assertThat(json(call("register", true)).path("outcome").asText()).isEqualTo("ALREADY_REGISTERED");
        admitted();
        assertThat(profile.jdbc.queryForObject("SELECT request_bytes FROM recovery_initial_normal", byte[].class)).isEqualTo(original);
        assertThat(profile.jdbc.queryForObject("SELECT COUNT(*) FROM recovery_initial_normal", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> profile.jdbc.update("UPDATE recovery_initial_normal SET numeric_epoch=numeric_epoch+1"))
            .hasStackTraceContaining("Initial normal registration is immutable");
        assertThatThrownBy(() -> profile.jdbc.update("DELETE FROM recovery_initial_normal"))
            .hasStackTraceContaining("Initial normal registration is retained");
    }

    void missingExternalAndStateConflicts() throws Exception {
        for (String name : new String[]{InitialNormalStore.REQUEST, InitialNormalStore.COMPLETE}) {
            Path path = profile.attempt.resolve("initial-authority").resolve(name);
            Path retained = profile.attempt.resolve("test-saved-" + name);
            Files.move(path, retained); denied(); Files.move(retained, path); admitted();
        }
        Path trusted = profile.attempt.resolve("trusted-original.json"), retained = profile.attempt.resolve("test-saved-original.json");
        Files.move(trusted, retained); denied(); Files.move(retained, trusted); admitted();
        profile.jdbc.update("UPDATE server_state SET service_mode='RECOVERY_PENDING' WHERE id=1");
        denied(); assertThat(call("register", true).statusCode()).isGreaterThanOrEqualTo(400);
        profile.jdbc.update("UPDATE server_state SET service_mode='NORMAL' WHERE id=1");
        long epoch = InitialNormalEvidence.parse(original).numericEpoch();
        profile.jdbc.update("UPDATE server_state SET recovery_epoch=? WHERE id=1", epoch + 1);
        denied(); profile.jdbc.update("UPDATE server_state SET recovery_epoch=? WHERE id=1", epoch); admitted();
        assertThat(Files.readAllBytes(trusted)).isEqualTo(original);
    }

    void closedBootstrapDenies() throws Exception {
        Files.createDirectory(profile.attempt.resolve("closed-authority"));
        denied(); assertThat(call("register", true).statusCode()).isGreaterThanOrEqualTo(400);
    }

    void admitted() throws Exception {
        var result = json(call("verify", false));
        assertThat(result.path("admitted").asBoolean()).isTrue();
        assertThat(result.path("installationId").asText()).isEqualTo(InitialNormalFixture.INSTALLATION.toString());
        assertThat(result.path("recordSha256").asText()).isEqualTo(InitialNormalEvidence.parse(original).sha256());
        assertThat(result.path("ordinaryWritesAllowed").asBoolean()).isFalse();
        assertThat(result.path("localDeletionAllowed").asBoolean()).isFalse();
    }

    private void denied() throws Exception { assertThat(json(call("verify", false)).path("admitted").asBoolean()).isFalse(); }

    private HttpResponse<String> call(String path, boolean post) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19437/__c1/" + path)).timeout(Duration.ofSeconds(15));
        if (post) request.POST(HttpRequest.BodyPublishers.noBody()); else request.GET();
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        Files.writeString(profile.attempt.resolve("c1-http-" + (++requests) + ".txt"),
            (post ? "POST " : "GET ") + path + "\n" + response.statusCode() + "\n" + response.body());
        return response;
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        return new ObjectMapper().readTree(response.body());
    }
}
