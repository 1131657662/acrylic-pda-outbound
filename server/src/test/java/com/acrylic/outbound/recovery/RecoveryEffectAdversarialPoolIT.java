package com.acrylic.outbound.recovery;

import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.zaxxer.hikari.*;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** A real one-connection pool must not reset a shared gate when returning its physical connection. */
class RecoveryEffectAdversarialPoolIT {
    @BeforeAll static void environment() throws Exception { EffectRealEnvironment.requireEnvironment(); }

    @RepeatedTest(3) void recycledPhysicalConnectionRetainsSharedFence() throws Exception {
        try (var f = new EffectAdversarialFixture(true, false)) {
            var opens = new AtomicInteger();
            try (var pool = pool(f.calls, opens)) {
                var control = RecoveryWriterLeaseTest.protocol();
                var original = new RecoveryAdmittedDataSource(pool, control.location(), control.checkpoint);
                var adapter = new RecoveryAdmittedDataSource(original, f.gate);
                var first = adapter.getConnection();
                var oldStatement = first.prepareStatement(f.writeSql());
                first.close();
                assertThat(pool.getHikariPoolMXBean().getIdleConnections()).isEqualTo(1);
                f.gate.fence();
                try (var next = adapter.getConnection()) {
                    assertThat(next).isNotSameAs(first);
                    assertThat(opens.get()).isEqualTo(1);
                    assertThatThrownBy(oldStatement::executeUpdate).hasMessage("CONTROL_CONNECTION_CLOSED");
                    assertThatThrownBy(() -> next.createStatement().executeUpdate(f.writeSql()))
                        .hasMessage("RECOVERY_LEASE_FENCED");
                    assertThat(f.calls.count("Statement.executeUpdate")).isZero();
                    assertThat(f.calls.count("PreparedStatement.executeUpdate")).isZero();
                    assertThat(f.observed()).isEqualTo("1:62:original;");
                    f.record("real-pool-reborrow|physicalOpens=1|oldProxyClosed=true|newWriteDelta=0");
                }
            }
        }
    }

    private static HikariDataSource pool(EffectDelegateCounter calls, AtomicInteger opens) {
        var settings = new HikariConfig();
        settings.setMaximumPoolSize(1);
        settings.setMinimumIdle(1);
        settings.setDataSource(new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException {
                opens.incrementAndGet();
                return (Connection) calls.wrap(EffectRealEnvironment.open(false));
            }
            @Override public Connection getConnection(String user, String password) throws SQLException {
                throw new SQLFeatureNotSupportedException("Pool control uses the exact test environment account");
            }
        });
        return new HikariDataSource(settings);
    }
}
