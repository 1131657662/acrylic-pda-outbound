package com.acrylic.outbound.recovery;

import java.sql.*;
import org.springframework.jdbc.datasource.AbstractDataSource;
import static org.assertj.core.api.Assertions.*;

/** One preserved synthetic table and one real writer per test; observer always opens independently. */
final class EffectRealFixture implements AutoCloseable {
    final String table = "t_" + java.util.UUID.randomUUID().toString().replace("-", "");
    final EffectTestAuthority authority = new EffectTestAuthority();
    final EffectDelegateCounter calls = new EffectDelegateCounter();
    final RecoveryEffectGate gate = RecoveryEffectGate.enabled(authority);
    final Connection connection;
    private final Connection physical;

    EffectRealFixture(boolean enabled, boolean multi) throws Exception {
        try (var setup = EffectRealEnvironment.open(false); var statement = setup.createStatement()) {
            statement.execute("CREATE TABLE " + table + "(id INT PRIMARY KEY, val VARCHAR(40) NOT NULL)");
        }
        physical = EffectRealEnvironment.open(multi);
        var source = new AbstractDataSource() {
            @Override public Connection getConnection() { return (Connection) calls.wrap(physical); }
            @Override public Connection getConnection(String user, String password) { return getConnection(); }
        };
        var control = RecoveryWriterLeaseTest.protocol();
        var adapter = new RecoveryAdmittedDataSource(source, control.location(), control.checkpoint);
        connection = enabled ? new RecoveryAdmittedDataSource(adapter, gate).getConnection() : adapter.getConnection();
    }

    String insert(int id) { return "INSERT INTO " + table + "(id,val) VALUES(" + id + ",'value')"; }
    void deny() { authority.denial = "RECOVERY_LEASE_OUTSTANDING"; }
    void write(int id) throws SQLException { connection.createStatement().executeUpdate(insert(id)); }

    PreparedStatement withoutSqlMemory() throws SQLException {
        var owner = (RecoveryConnectionHandler) java.lang.reflect.Proxy.getInvocationHandler(connection);
        Object counted = calls.wrap(physical.prepareStatement(insert(1)));
        return (PreparedStatement) RecoveryJdbcProxy.wrap(counted, connection, owner);
    }

    int count() throws SQLException {
        try (var observer = EffectRealEnvironment.open(false); var statement = observer.createStatement();
                var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertThat(rows.next()).isTrue(); return rows.getInt(1);
        }
    }

    String value(int id) throws SQLException {
        try (var observer = EffectRealEnvironment.open(false);
                var statement = observer.prepareStatement("SELECT val FROM " + table + " WHERE id=?")) {
            statement.setInt(1, id);
            try (var rows = statement.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    void record(String scenario) throws Exception {
        EffectRealEnvironment.record("REAL|" + scenario + "|table=" + table + "|committedRows=" + count()
            + "|gateChecks=" + authority.checks + "|authority=MODEL");
    }

    @Override public void close() throws SQLException { connection.close(); }
}
