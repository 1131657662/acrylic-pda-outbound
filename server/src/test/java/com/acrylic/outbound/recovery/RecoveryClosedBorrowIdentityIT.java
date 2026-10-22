package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import static org.assertj.core.api.Assertions.*;

/** Actual adapter borrows on distinct instances with identical MODEL registration rows/schema. */
class RecoveryClosedBorrowIdentityIT {
    @Test void copiedInstanceBorrowIsRefusedWhileSourceRemainsUsable() throws Exception {
        EffectRealEnvironment.requireEnvironment();
        Path output = Path.of(System.getenv("B11_EFFECT_EVIDENCE_DIR"));
        var target = ClosedBorrowTarget.source();
        try (var original = EffectRealEnvironment.open(false); var copied = target.getConnection()) {
            ClosedBorrowTarget.requireIdentity(copied, output);
            var sourceJdbc = new JdbcTemplate(new SingleConnectionDataSource(original, true));
            var targetJdbc = new JdbcTemplate(new SingleConnectionDataSource(copied, true));
            Flyway.configure().dataSource(sourceJdbc.getDataSource()).load().migrate();
            Flyway.configure().dataSource(targetJdbc.getDataSource()).load().migrate();
            assertThat(targetJdbc.queryForObject("SELECT DATABASE()", String.class))
                .isEqualTo(sourceJdbc.queryForObject("SELECT DATABASE()", String.class));
            assertThat(targetJdbc.queryForObject("SELECT @@server_uuid", String.class))
                .isNotEqualTo(sourceJdbc.queryForObject("SELECT @@server_uuid", String.class));
            var fixture = ClosedAdmissionInstanceModel.create(sourceJdbc, output);
            ClosedBorrowModelProtocol.install(fixture);
            ClosedAdmissionInstanceModel.copy(sourceJdbc, targetJdbc);
            exercise(fixture, target, sourceJdbc, targetJdbc);
        }
    }

    private static void exercise(RecoveryControlFixture fixture, DataSource target,
            JdbcTemplate sourceJdbc, JdbcTemplate targetJdbc) throws Exception {
        var rows = ClosedAdmissionIdentityObservation.rows(sourceJdbc);
        var files = ClosedAdmissionIdentityObservation.files(fixture.root.getParent());
        manifest(fixture, "external-before.json", files);
        var registry = new RecoveryOperationRegistry(fixture.location(), fixture.checkpoint);
        var token = new RecoveryOperationToken(UUID.randomUUID(), RecoveryOperationKind.HTTP_REQUEST,
            fixture.installation, registry.startup().recordSha256(), null);
        var root = RecoveryControlLock.locked(fixture.root, () -> registry.register(token));
        try (root) {
            root.bindDispatch(Thread.currentThread());
            try { borrows(fixture, target, registry, root); }
            finally { root.unbindAll(); }
        }
        assertThat(registry.liveRoots()).isZero();
        assertThat(ClosedAdmissionIdentityObservation.rows(sourceJdbc)).isEqualTo(rows);
        assertThat(ClosedAdmissionIdentityObservation.rows(targetJdbc)).isEqualTo(rows);
        assertThat(ClosedAdmissionIdentityObservation.files(fixture.root.getParent())).isEqualTo(files);
        manifest(fixture, "external-after.json", files);
        try (var exclusive = RecoveryControlLock.locked(fixture.root,
                () -> RecoveryWriterLease.acquireClosed(fixture.root))) {
            assertThat(exclusive.confirmed()).isTrue();
        }
        EffectRealEnvironment.record("REAL|adapter-conservation|tables=" + rows.size()
            + "|externalFiles=" + files.size() + "|roots=0|exclusiveWriterAvailable");
    }

    private static void borrows(RecoveryControlFixture fixture, DataSource target,
            RecoveryOperationRegistry registry, RecoveryOperationLease root) throws Exception {
        var count = new AtomicInteger();
        DataSource switching = new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException {
                return count.incrementAndGet() == 2 ? target.getConnection() : EffectRealEnvironment.open(false);
            }

            @Override public Connection getConnection(String user, String password) throws SQLException {
                return getConnection();
            }
        };
        var observation = new ClosedBorrowObservation(fixture);
        var adapter = new RecoveryRequestAdmittedDataSource(switching, registry,
            new RecoveryRequestAdmittedDataSource.Checks(RecoveryEffectGate.disabled(), observation));
        assertUsable(adapter, "source-before");
        assertThatThrownBy(adapter::getConnection).isInstanceOf(SQLNonTransientConnectionException.class)
            .hasMessage("RECOVERY_DATABASE_ADMISSION_REFUSED:DATABASE_SERVER_UUID");
        assertThat(root.openSubOperations()).isZero();
        assertThat(root.phase()).isEqualTo(RecoveryOperationPhase.SERVING);
        observation.allClosed();
        assertUsable(adapter, "source-after");
        assertThat(count.get()).isEqualTo(3);
        assertThat(observation.calls).isEqualTo(3);
        observation.allClosed();
        assertThat(root.openSubOperations()).isZero();
    }

    private static void assertUsable(DataSource adapter, String label) throws Exception {
        try (var connection = adapter.getConnection(); var query = connection.createStatement();
                var result = query.executeQuery("SELECT DATABASE()")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo(EffectRealEnvironment.USER);
        }
        EffectRealEnvironment.record("REAL|adapter-business|" + label + "|querySucceeded");
    }

    private static void manifest(RecoveryControlFixture fixture, String name, Map<String, String> hashes) throws Exception {
        Files.writeString(fixture.root.getParent().getParent().resolve(name),
            RecoveryControlFixture.JSON.writeValueAsString(hashes), StandardOpenOption.CREATE_NEW);
    }
}
