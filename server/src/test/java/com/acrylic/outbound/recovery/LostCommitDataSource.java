package com.acrylic.outbound.recovery;

import java.lang.reflect.*;
import java.sql.*;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/** Test-only wire boundary: actual MySQL commit succeeds, then its acknowledgement is lost. */
final class LostCommitDataSource extends DelegatingDataSource {
    @FunctionalInterface interface AfterCommit { void run() throws SQLException; }
    private final AfterCommit afterCommit;
    LostCommitDataSource(DataSource original) { this(original, () -> { }); }
    LostCommitDataSource(DataSource original, AfterCommit afterCommit) {
        super(original);
        this.afterCommit = java.util.Objects.requireNonNull(afterCommit);
    }

    @Override public Connection getConnection() throws SQLException { return lost(super.getConnection()); }
    @Override public Connection getConnection(String user, String password) throws SQLException {
        return lost(super.getConnection(user, password));
    }

    private Connection lost(Connection original) {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
            (proxy, method, args) -> {
                try {
                    Object result = method.invoke(original, args);
                    if (method.getName().equals("commit")) {
                        afterCommit.run();
                        throw new SQLException("Synthetic lost commit acknowledgement", "08007");
                    }
                    return result;
                } catch (InvocationTargetException error) { throw error.getCause(); }
            });
    }
}
