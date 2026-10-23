package com.acrylic.outbound.recovery;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Actual product CSRF/security/Session consumer; responses and table hashes remain private evidence. */
final class SpringClosedAdmissionHttpChecks {
    private final SpringClosedAdmissionFixture fixture;
    private final ConfigurableApplicationContext context;
    private final HttpClient client;
    private final URI endpoint;

    SpringClosedAdmissionHttpChecks(SpringClosedAdmissionFixture fixture, ConfigurableApplicationContext context) {
        this.fixture = fixture; this.context = context;
        client = HttpClient.newBuilder().cookieHandler(new CookieManager()).connectTimeout(Duration.ofSeconds(10)).build();
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        endpoint = URI.create("http://127.0.0.1:" + port + "/api/v1/auth/csrf");
    }

    void wiring() {
        DataSource admitted = context.getBean(DataSource.class);
        assertThat(admitted).isInstanceOf(RecoveryRequestAdmittedDataSource.class);
        assertThat(context.getBean(JdbcTemplate.class).getDataSource()).isSameAs(admitted);
        var repository = context.getBean(JdbcIndexedSessionRepository.class);
        var jdbc = (JdbcTemplate) ReflectionTestUtils.getField(repository, "jdbcOperations");
        assertThat(jdbc).isNotNull();
        assertThat(jdbc.getDataSource()).isSameAs(admitted);
        assertThatThrownBy(admitted::getConnection).hasMessage("RECOVERY_OPERATION_REQUIRED");
        assertThat(context.getBean(RecoveryInitializationWindow.class).root()).isNull();
        assertThat(registry().liveRoots()).isZero();
    }

    void normalSession() throws Exception {
        var before = fixture.tables();
        fixture.save("tables-before-http.json", before);
        success("normal-first");
        assertThat(fixture.raw.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Long.class)).isEqualTo(1L);
        assertThat(fixture.raw.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES", Long.class)).isPositive();
        String original = fixture.raw.queryForObject("SELECT SESSION_ID FROM SPRING_SESSION", String.class);
        success("normal-reuse");
        assertThat(fixture.raw.queryForList("SELECT SESSION_ID FROM SPRING_SESSION", String.class)).containsExactly(original);
        var after = fixture.tables();
        fixture.save("tables-after-http.json", after);
        var stableBefore = new TreeMap<>(before); var stableAfter = new TreeMap<>(after);
        stableBefore.keySet().removeIf(SpringClosedAdmissionHttpChecks::sessionTable);
        stableAfter.keySet().removeIf(SpringClosedAdmissionHttpChecks::sessionTable);
        assertThat(stableAfter).isEqualTo(stableBefore);
    }

    void rejectPendingRecovery() throws Exception {
        fixture.pendingRecovery();
        var before = fixture.tables();
        fixture.save("tables-before-refusal.json", before);
        var response = request("pending-recovery");
        assertThat(response.statusCode()).as("closed admission refuses without a successful Session save").isEqualTo(503);
        assertThat(response.body()).contains("RECOVERY_DATABASE_ADMISSION_REFUSED")
            .contains("\"serverRecoveryEpoch\":\"unknown\"").contains("\"serviceMode\":\"UNAVAILABLE\"");
        drained();
        var after = fixture.tables();
        assertThat(after).isEqualTo(before);
        fixture.save("tables-after-refusal.json", after);
        fixture.restoreOwnedMode();
        success("source-again");
    }

    private void success(String label) throws Exception {
        var response = request(label);
        assertThat(response.statusCode()).as(label + ": " + response.body()).isEqualTo(200);
        assertThat(RecoveryControlFixture.JSON.readTree(response.body()).path("data").path("token").asText()).isNotBlank();
        drained();
    }

    private HttpResponse<String> request(String label) throws Exception {
        var response = client.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        fixture.save(label + "-http.json", Map.of("uri", endpoint.toString(), "status", response.statusCode(),
            "headers", response.headers().map(), "body", response.body()));
        return response;
    }

    private RecoveryOperationRegistry registry() { return context.getBean(RecoveryOperationRegistry.class); }
    private static boolean sessionTable(String name) {
        return name.equalsIgnoreCase("SPRING_SESSION") || name.equalsIgnoreCase("SPRING_SESSION_ATTRIBUTES");
    }
    private void drained() {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(registry().liveRoots()).isZero());
    }
}
