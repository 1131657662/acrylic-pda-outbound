package com.acrylic.outbound.recovery;

import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** Real driver underneath; only its completion acknowledgement is replaced by an explicit fault. */
final class CompletionFaultDataSource extends AbstractDataSource {
    private final Path home;
    private final AtomicReference<String> nextFault = new AtomicReference<>();

    CompletionFaultDataSource(Path home) { this.home = home; }

    void arm(String fault) { nextFault.set(fault); }

    @Override public Connection getConnection() throws SQLException {
        Connection real = EffectRealEnvironment.open(false);
        String fault = nextFault.getAndSet(null);
        record("borrow|fault=" + fault);
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class}, handler(real, fault));
    }

    @Override public Connection getConnection(String user, String password) throws SQLException {
        throw new SQLException("test capability does not accept replacement credentials");
    }

    private InvocationHandler handler(Connection real, String fault) {
        return (proxy, method, args) -> {
            try {
                Object result = method.invoke(real, args);
                if (method.getName().equals("rollback") || method.getName().equals("close")) {
                    record("driver-returned|" + method.getName());
                    if (fault != null && fault.startsWith(method.getName() + ":")) fail(fault);
                }
                return result;
            } catch (InvocationTargetException failure) { throw failure.getCause(); }
        };
    }

    private void fail(String fault) throws SQLException {
        record("MODEL-acknowledgement-fault|" + fault);
        String message = "test completion acknowledgement lost: " + fault;
        switch (fault.split(":")[1]) {
            case "sql" -> throw new SQLException(message, "08006");
            case "runtime" -> throw new IllegalStateException(message);
            case "error" -> throw new AssertionError(message);
            default -> throw new IllegalArgumentException(fault);
        }
    }

    private synchronized void record(String text) throws SQLException {
        try {
            Files.writeString(home.resolve("driver.tsv"), text + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (java.io.IOException failure) { throw new SQLException("test receipt write failed", failure); }
    }
}
