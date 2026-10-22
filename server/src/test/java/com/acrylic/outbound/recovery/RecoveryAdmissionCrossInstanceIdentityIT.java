package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.sql.*;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.assertThat;

/** Two actual server UUIDs with the same schema and byte-equivalent MODEL admission rows. */
class RecoveryAdmissionCrossInstanceIdentityIT {
    @Test void copiedAdmissionRowsOnAnotherServerAreRefusedByPhysicalUuid() throws Exception {
        EffectRealEnvironment.requireEnvironment();
        Path output = Path.of(System.getenv("B11_EFFECT_EVIDENCE_DIR"));
        try (var original = EffectRealEnvironment.open(false); var copied = openTarget(output)) {
            var source = new SingleConnectionDataSource(original, true);
            var target = new SingleConnectionDataSource(copied, true);
            migrate(source); migrate(target);
            var jdbc = new JdbcTemplate(source);
            var other = new JdbcTemplate(target);
            requireDifferentServers(jdbc, other);
            var fixture = ClosedAdmissionInstanceModel.create(jdbc, output);
            ClosedAdmissionInstanceModel.copy(jdbc, other);
            compare(source, target, fixture);
        }
    }

    private static void migrate(SingleConnectionDataSource source) {
        Flyway.configure().dataSource(source).load().migrate();
    }

    private static void compare(SingleConnectionDataSource source, SingleConnectionDataSource target,
            RecoveryControlFixture fixture) throws Exception {
        var expected = ClosedAdmissionIdentityObservation.rows(new JdbcTemplate(source));
        assertThat(ClosedAdmissionIdentityObservation.rows(new JdbcTemplate(target))).isEqualTo(expected);
        Path control = fixture.root.getParent(), output = control.getParent();
        var files = ClosedAdmissionIdentityObservation.files(control);
        String before = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(files);
        Files.writeString(output.resolve("external-before.json"), before, StandardOpenOption.CREATE_NEW);
        assertAdmitted(ClosedAdmissionIdentityObservation.observe(source, fixture, "source-before"));
        conserved(source, target, expected);
        var denied = ClosedAdmissionIdentityObservation.observe(target, fixture, "copied-instance");
        assertThat(denied.admitted()).isFalse();
        assertThat(denied.gaps()).containsExactly("DATABASE_SERVER_UUID");
        conserved(source, target, expected);
        assertAdmitted(ClosedAdmissionIdentityObservation.observe(source, fixture, "source-after"));
        conserved(source, target, expected);
        assertThat(ClosedAdmissionIdentityObservation.files(control)).isEqualTo(files);
        Files.writeString(output.resolve("external-after.json"),
            new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(ClosedAdmissionIdentityObservation.files(control)),
            StandardOpenOption.CREATE_NEW);
        EffectRealEnvironment.record("REAL|conservation|tables=" + expected.size() + "|externalFiles=" + files.size());
    }

    private static void assertAdmitted(RecoveryAdmissionVerifier.Result result) {
        assertThat(result.admitted()).isTrue();
        assertThat(result.gaps()).isEmpty();
    }

    private static void conserved(SingleConnectionDataSource source, SingleConnectionDataSource target,
            Map<String, String> expected) throws Exception {
        assertThat(ClosedAdmissionIdentityObservation.rows(new JdbcTemplate(source))).isEqualTo(expected);
        assertThat(ClosedAdmissionIdentityObservation.rows(new JdbcTemplate(target))).isEqualTo(expected);
    }

    private static void requireDifferentServers(JdbcTemplate source, JdbcTemplate target) {
        assertThat(target.queryForObject("SELECT DATABASE()", String.class))
            .isEqualTo(source.queryForObject("SELECT DATABASE()", String.class));
        assertThat(target.queryForObject("SELECT @@server_uuid", String.class))
            .isNotEqualTo(source.queryForObject("SELECT @@server_uuid", String.class));
    }

    private static Connection openTarget(Path output) throws Exception {
        String url = System.getenv("OUTBOUND_COPY_DB_URL"), user = System.getenv("OUTBOUND_COPY_DB_USER");
        var match = java.util.regex.Pattern.compile("jdbc:mysql://127\\.0\\.0\\.1:(19[4-9][0-9]{2})/(p5_lost_[0-9]+)\\?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC")
            .matcher(url == null ? "" : url);
        assertThat(match.matches()).as("explicit loopback isolated target required").isTrue();
        assertThat(user).isEqualTo(match.group(2)).isEqualTo(EffectRealEnvironment.USER);
        assertThat(url).isNotEqualTo(EffectRealEnvironment.URL);
        var connection = DriverManager.getConnection(url, user, System.getenv("OUTBOUND_COPY_DB_PASSWORD"));
        try {
            requireTargetIdentity(connection, Integer.parseInt(match.group(1)), output);
            return connection;
        } catch (Exception | Error failure) {
            connection.close();
            throw failure;
        }
    }

    private static void requireTargetIdentity(Connection connection, int port, Path output) throws Exception {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT @@port,@@datadir,CURRENT_USER(),@@server_uuid,DATABASE()")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(port);
            Path local = output.toRealPath();
            while (local != null && !local.getFileName().toString().equals(".local")) local = local.getParent();
            assertThat(local).isNotNull();
            Path datadir = Path.of(rows.getString(2).replace('\\', '/')).toRealPath();
            assertThat(datadir).startsWith(local.resolve("mysql" + port + "-r09batch"));
            assertThat(rows.getString(3)).isEqualTo(EffectRealEnvironment.USER + "@127.0.0.1");
            assertThat(rows.getString(5)).isEqualTo(EffectRealEnvironment.USER);
            EffectRealEnvironment.record("REAL|target-identity|port=" + port + "|datadir=" + datadir
                + "|user=" + rows.getString(3) + "|uuid=" + rows.getString(4) + "|schema=" + rows.getString(5));
        }
    }
}
