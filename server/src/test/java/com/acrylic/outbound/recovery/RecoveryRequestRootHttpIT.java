package com.acrylic.outbound.recovery;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * S3 field run: a real container, a real Spring Session JDBC store and the admitted request entry
 * enabled. Asserts one operation root per request while it is served, one session row per session
 * step, the error and async dispatches reusing the same root, no root left behind, and the session
 * save happening inside a live root.
 */
class RecoveryRequestRootHttpIT {
    private static final int APP_PORT = Integer.getInteger("s3.http.port",
        Integer.parseInt(System.getenv().getOrDefault("S3_HTTP_PORT", "19458")));

    @Test void everyRealDispatchOfARequestIsCoveredByOneRootAndTheSessionSaveIsInsideIt() throws Exception {
        EffectRealEnvironment.requireEnvironment();
        migrate();
        var control = RecoveryWriterLeaseTest.protocol();
        Path evidence = Path.of(System.getenv("B11_EFFECT_EVIDENCE_DIR"));
        var transcript = new StringBuilder();

        try (var context = start(control, evidence)) {
            var registry = context.getBean(RecoveryOperationRegistry.class);
            var observation = context.getBean(RecoveryRootObservation.class);
            var client = client();
            retireStartupRoot(context);

            try (var session = connection()) {
                transcript.append(resetSessionStore(session));
                transcript.append(registration(context)).append(step(client, "/__s3/session", 200))
                    .append(sessionRows(session, "s3-session"));
                transcript.append(step(client, "/__s3/error", 500)).append(sessionRows(session, "s3-error"));
                transcript.append(step(client, "/__s3/async", 200));
            }

            assertThat(observation.asyncBorrows())
                .as("the async worker carries the request context but no dispatch binding")
                .containsExactly("RECOVERY_OPERATION_CONTEXT_LOST");
            assertThat(observation.liveRootsDuringRequests())
                .as("exactly one root exists while each request is served").containsOnly(1);
            assertThat(observation.saves()).as("the framework saved the session at least once").isNotEmpty();
            assertThat(observation.saves()).as("every session save ran inside a live root")
                .allMatch(save -> save.outcome().equals("BOUND"));
            assertThat(observation.passes()).extracting(RecoveryRootObservation.Pass::dispatcherType)
                .as("the real container dispatched REQUEST, ERROR and ASYNC through the entry")
                .contains("REQUEST", "ERROR", "ASYNC");
            assertThat(observation.passes()).as("every pass saw the request's root marker")
                .allMatch(RecoveryRootObservation.Pass::rootMarker);
            assertThat(registry.liveRoots()).as("no root survives its request").isZero();
            assertThat(springSessionThreads()).as("session cleanup stays disabled in this run").isEmpty();

            transcript.append("asyncBorrow=").append(observation.asyncBorrows())
                .append("\nliveRootsDuringRequests=").append(observation.liveRootsDuringRequests())
                .append("\nsaves=").append(observation.saves())
                .append("\npasses=").append(observation.passes())
                .append("\nspringSessionThreads=").append(springSessionThreads())
                .append("\nliveRootsAfter=").append(registry.liveRoots()).append('\n');
            Files.writeString(evidence.resolve("s3-session-root.txt"), transcript.toString());
            EffectRealEnvironment.record("S3|REAL|" + transcript.toString().replace('\n', '|'));
        }
    }

    private ConfigurableApplicationContext start(RecoveryControlFixture control, Path evidence) {
        return new SpringApplication(com.acrylic.outbound.OutboundApplication.class,
            RecoveryRootProbeConfiguration.class)
            .run("--server.address=127.0.0.1", "--server.port=" + APP_PORT,
                "--spring.profiles.active=s3-probe",
                "--server.servlet.session.cookie.secure=false",
                "--spring.flyway.enabled=false", "--spring.session.jdbc.cleanup-cron=-",
                "--outbound.worker.enabled=false", "--outbound.bootstrap.enabled=false",
                "--outbound.storage=" + evidence.resolve("storage"),
                "--outbound.recovery.admission.enabled=true",
                "--outbound.recovery.root=" + control.root,
                "--outbound.recovery.installation-id=" + control.installation,
                "--outbound.recovery.restore-roots=" + control.restoreRoot,
                "--outbound.recovery.checkpoint=" + control.checkpoint);
    }

    /**
     * The run's explicit initialization capability (see the probe configuration) is retired as soon
     * as the context is up, so the request steps start from a state with no live root at all.
     */
    private static void retireStartupRoot(ConfigurableApplicationContext context) {
        RecoveryOperationLease root = context.getBean(RecoveryOperationLease.class);
        root.unbindDispatch(Thread.currentThread());
        root.close();
    }

    private static String registration(ConfigurableApplicationContext context) {
        var entry = context.getBean("recoveryRequestAdmissionFilterRegistration",
            org.springframework.boot.web.servlet.AbstractFilterRegistrationBean.class);
        var session = context.getBean("sessionRepositoryFilterRegistration",
            org.springframework.boot.web.servlet.AbstractFilterRegistrationBean.class);
        assertThat(entry.getOrder()).as("the request entry precedes the framework session filter")
            .isLessThan(session.getOrder());
        assertThat(session.getOrder()).as("the framework session filter keeps its documented order")
            .isEqualTo(-2147483598);
        assertThat(session.determineDispatcherTypes()).containsExactlyInAnyOrder(
            jakarta.servlet.DispatcherType.REQUEST, jakarta.servlet.DispatcherType.ASYNC,
            jakarta.servlet.DispatcherType.ERROR);
        return "entryOrder=" + entry + " sessionOrder=" + session.getOrder()
            + " sessionDispatcherTypes=" + session.determineDispatcherTypes() + '\n';
    }

    private static String step(HttpClient client, String path, int expected) throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + APP_PORT + path))
            .timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("real HTTP " + path).isEqualTo(expected);
        return "step " + path + " -> " + response.statusCode() + " body=" + response.body().replace('\n', ' ') + '\n';
    }

    private static String sessionRows(Connection connection, String attribute) throws Exception {
        int rows = count(connection, "SELECT COUNT(*) FROM SPRING_SESSION");
        int attributes = count(connection, "SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES WHERE ATTRIBUTE_NAME='"
            + attribute + "'");
        assertThat(rows).as("one session row per served request").isEqualTo(1);
        assertThat(attributes).as("the request's session write reached the store: " + attribute).isEqualTo(1);
        return "session rows=" + rows + " " + attribute + "=" + attributes + '\n';
    }

    private static int count(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getInt(1);
        }
    }

    private static void migrate() throws Exception {
        var flyway = Flyway.configure()
            .dataSource(System.getenv("OUTBOUND_DB_URL"), System.getenv("OUTBOUND_DB_USER"),
                System.getenv("OUTBOUND_DB_PASSWORD"))
            .load();
        var result = flyway.migrate();
        assertThat(flyway.info().current()).as("the isolated schema is migrated to the current version").isNotNull();
        EffectRealEnvironment.record("S3|migrate|executed=" + result.migrationsExecuted
            + "|version=" + flyway.info().current().getVersion());
    }

    /**
     * The isolated store is reset per run so the per-step assertions are exact when the runner
     * reuses its instance. It touches only this run's own SPRING_SESSION tables.
     */
    private static String resetSessionStore(Connection connection) throws Exception {
        int sessions = count(connection, "SELECT COUNT(*) FROM SPRING_SESSION");
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM SPRING_SESSION_ATTRIBUTES");
            statement.executeUpdate("DELETE FROM SPRING_SESSION");
        }
        assertThat(count(connection, "SELECT COUNT(*) FROM SPRING_SESSION")).isZero();
        return "storeReset previousSessions=" + sessions + '\n';
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(System.getenv("OUTBOUND_DB_URL"), System.getenv("OUTBOUND_DB_USER"),
            System.getenv("OUTBOUND_DB_PASSWORD"));
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager()).connectTimeout(Duration.ofSeconds(10)).build();
    }

    private static List<String> springSessionThreads() {
        return Thread.getAllStackTraces().keySet().stream().map(Thread::getName)
            .filter(name -> name.startsWith("spring-session-")).sorted().toList();
    }
}
