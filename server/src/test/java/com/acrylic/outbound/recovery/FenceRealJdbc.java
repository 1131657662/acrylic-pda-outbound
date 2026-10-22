package com.acrylic.outbound.recovery;

import java.lang.reflect.Proxy;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;

/** Real driver effects behind the product proxy; observer has an independent connection. */
final class FenceRealJdbc implements AutoCloseable {
    final String table;
    final EffectDelegateCounter calls = new EffectDelegateCounter();
    final Connection connection;

    FenceRealJdbc(RecoveryEffectGate gate) throws Exception { this(gate, createTable()); }
    FenceRealJdbc(RecoveryEffectGate gate, String table) throws Exception {
        this(gate, table, false);
    }
    FenceRealJdbc(RecoveryEffectGate gate, String table, boolean loseCommit) throws Exception {
        this.table = table;
        var physical = (Connection)calls.wrap(EffectRealEnvironment.open(false));
        if (loseCommit) physical = new LostCommitDataSource(
            new org.springframework.jdbc.datasource.SingleConnectionDataSource(physical, false)).getConnection();
        var admission = RecoveryWriterLeaseTest.protocol(); // MODEL legacy admission only, never the effect Authority.
        var lease = RecoveryControlLock.locked(admission.root, () -> RecoveryWriterLease.acquire(admission.root));
        var owner = new RecoveryConnectionHandler(physical, lease);
        owner.useEffectGate(gate);
        connection = (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, owner);
    }
    private static String createTable() throws Exception {
        String name = "f_" + java.util.UUID.randomUUID().toString().replace("-", "");
        try (var connection = EffectRealEnvironment.open(false); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + name + "(id INT PRIMARY KEY, val VARCHAR(40))");
        }
        return name;
    }
    String insert(int id) { return "INSERT INTO " + table + " VALUES (" + id + ",'value')"; }
    int count() throws Exception {
        try (var observer = EffectRealEnvironment.open(false); var statement = observer.createStatement();
                var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertThat(rows.next()).isTrue(); return rows.getInt(1);
        }
    }
    void rejected(Statement old, String code) throws Exception {
        int before = calls.count("Statement.executeUpdate"), rows = count();
        assertThatThrownBy(() -> old.executeUpdate(insert(99))).hasMessage(code);
        assertThat(calls.count("Statement.executeUpdate")).isEqualTo(before);
        assertThat(count()).isEqualTo(rows);
        EffectRealEnvironment.record("REAL|rejection|code=" + code + "|table=" + table
            + "|delegateDelta=0|observerDelta=0|authority=RecoveryFenceAuthority");
    }
    @Override public void close() throws Exception { connection.close(); }
}
