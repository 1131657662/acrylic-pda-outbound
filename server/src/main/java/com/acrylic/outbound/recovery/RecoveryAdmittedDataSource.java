package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Lifecycle adapter for an explicitly admitted database writer. It is not a Spring bean;
 * callers must opt in so Flyway and read-only infrastructure cannot bypass the control contract.
 */
public final class RecoveryAdmittedDataSource implements DataSource {
    private final DataSource delegate;
    private final RecoveryControlLocation location;
    private final Path checkpoint;
    private final RecoveryControlSnapshot startup;
    private final RecoveryEffectGate effects;

    public RecoveryAdmittedDataSource(DataSource delegate, RecoveryControlLocation location, Path checkpoint) {
        this.delegate = Objects.requireNonNull(delegate);
        this.effects = RecoveryEffectGate.disabled();
        try {
            this.location = location.checked();
            this.checkpoint = this.location.checkpoint(checkpoint);
            this.startup = new RecoveryControlReader(this.location).read(this.checkpoint);
        } catch (IOException error) {
            throw new RecoveryControlException("CONTROL_AUTHORITY_UNAVAILABLE", error);
        }
    }

    /** Explicit offline opt-in only; no Spring datasource/HTTP/Session wiring uses this constructor. */
    public RecoveryAdmittedDataSource(RecoveryAdmittedDataSource admitted, RecoveryEffectGate effects) {
        this.delegate = admitted.delegate;
        this.location = admitted.location;
        this.checkpoint = admitted.checkpoint;
        this.startup = admitted.startup;
        this.effects = Objects.requireNonNull(effects);
    }

    @Override public Connection getConnection() throws SQLException {
        return admit(RecoveryAdmissionProbe.NOOP);
    }

    /** Test seam: identical to {@link #getConnection()} plus a hook inside the serialized boundary. */
    Connection admit(RecoveryAdmissionProbe probe) throws SQLException {
        RecoveryWriterLease lease = acquire();
        return borrow(delegate::getConnection, lease, probe);
    }

    @Override public Connection getConnection(String user, String password) throws SQLException {
        RecoveryWriterLease lease = acquire();
        return borrow(() -> delegate.getConnection(user, password), lease, RecoveryAdmissionProbe.NOOP);
    }

    @FunctionalInterface
    private interface ConnectionSupplier { Connection get() throws SQLException; }

    private Connection borrow(ConnectionSupplier supplier, RecoveryWriterLease lease, RecoveryAdmissionProbe probe) throws SQLException {
        Connection physical;
        try { physical = supplier.get(); }
        catch (SQLException | RuntimeException error) { closeLease(lease, error); throw error; }
        if (physical == null) {
            effects.fence();
            lease.poison();
            throw new SQLException("CONTROL_CONNECTION_UNAVAILABLE");
        }
        try { return wrap(physical, lease, probe); }
        catch (SQLException | RuntimeException error) {
            effects.fence();
            lease.poison();
            try { physical.close(); } catch (SQLException close) { error.addSuppressed(close); }
            throw error;
        }
    }

    private RecoveryWriterLease acquire() throws SQLException {
        try {
            RecoveryControlLocation checked = location.checked();
            return RecoveryControlLock.locked(checked.root(), () -> {
                RecoveryControlLocation current = location.checked();
                RecoveryControlFormat.require(checked.equals(current), "CONTROL_AUTHORITY_CHANGED");
                Path trustedCheckpoint = current.checkpoint(checkpoint);
                RecoveryControlSnapshot now = RecoveryControlHistory.load(current, trustedCheckpoint);
                RecoveryControlFormat.require(startup.equals(now), "CONTROL_INSTANCE_STALE");
                return RecoveryWriterLease.acquire(current.root());
            });
        } catch (IOException error) { throw new SQLException(error.getMessage(), error); }
    }

    private Connection wrap(Connection connection, RecoveryWriterLease lease, RecoveryAdmissionProbe probe) throws SQLException {
        var handler = new RecoveryConnectionHandler(connection, lease, probe);
        handler.useEffectGate(effects);
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class}, handler);
    }

    private static void closeLease(RecoveryWriterLease lease, Throwable original) {
        try { lease.close(); } catch (IOException close) { original.addSuppressed(close); }
    }

    @Override public <T> T unwrap(Class<T> type) throws SQLException {
        if (type.isInstance(this)) return type.cast(this);
        throw new SQLException("CONTROL_DATASOURCE_UNWRAP_FORBIDDEN");
    }
    @Override public boolean isWrapperFor(Class<?> type) { return type.isInstance(this); }
    @Override public PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }
    @Override public void setLogWriter(PrintWriter out) throws SQLException { delegate.setLogWriter(out); }
    @Override public void setLoginTimeout(int seconds) throws SQLException { delegate.setLoginTimeout(seconds); }
    @Override public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return delegate.getParentLogger(); }
}
