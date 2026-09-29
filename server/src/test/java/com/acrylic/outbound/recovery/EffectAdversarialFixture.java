package com.acrylic.outbound.recovery;

import java.sql.*;
import org.springframework.jdbc.datasource.AbstractDataSource;
import static org.assertj.core.api.Assertions.*;

/** Real isolated driver below the gate; MODEL authority and admission files only. */
final class EffectAdversarialFixture implements AutoCloseable {
    final String table = "a_" + java.util.UUID.randomUUID().toString().replace("-", "");
    final EffectTestAuthority authority = new EffectTestAuthority();
    final RecoveryEffectGate gate = RecoveryEffectGate.enabled(authority);
    final EffectDelegateCounter calls = new EffectDelegateCounter();
    final RecoveryAdmittedDataSource adapter;
    final Connection connection;

    EffectAdversarialFixture(boolean enabled, boolean locator) throws Exception {
        setup();
        var source = new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException {
                return (Connection) calls.wrap(locator ? EffectRealEnvironment.locators() : EffectRealEnvironment.open(true));
            }
            @Override public Connection getConnection(String user, String password) throws SQLException {
                assertThat(user).isEqualTo(EffectRealEnvironment.USER);
                return (Connection) calls.wrap(DriverManager.getConnection(EffectRealEnvironment.URL
                    + (locator ? "&emulateLocators=true" : "&allowMultiQueries=true"), user, password));
            }
        };
        var control = RecoveryWriterLeaseTest.protocol();
        var original = new RecoveryAdmittedDataSource(source, control.location(), control.checkpoint);
        adapter = enabled ? new RecoveryAdmittedDataSource(original, gate) : original;
        connection = adapter.getConnection();
    }

    private void setup() throws Exception {
        try (var c = EffectRealEnvironment.open(false); var s = c.createStatement()) {
            s.execute("CREATE TABLE " + table + "(id INT PRIMARY KEY,b BLOB,c TEXT)");
            s.execute("INSERT INTO " + table + " VALUES(1,'b','original')");
        }
    }

    ResultSet rows() throws SQLException {
        var s = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        var r = s.executeQuery("SELECT id,b,c FROM " + table);
        assertThat(r.next()).isTrue();
        return r;
    }

    String observed() throws SQLException {
        try (var c = EffectRealEnvironment.open(false); var s = c.createStatement();
             var r = s.executeQuery("SELECT id,HEX(b),c FROM " + table + " ORDER BY id")) {
            StringBuilder values = new StringBuilder();
            while (r.next()) values.append(r.getInt(1)).append(':').append(r.getString(2))
                .append(':').append(r.getString(3)).append(';');
            return values.toString();
        }
    }

    String writeSql() { return "INSERT INTO " + table + " VALUES(2,'b','new')"; }
    void record(String text) throws Exception {
        EffectRealEnvironment.record("REAL|" + text + "|table=" + table + "|observer=" + observed()
            + "|gateChecks=" + authority.checks + "|authority=MODEL");
    }
    @Override public void close() throws SQLException { connection.close(); }
}
