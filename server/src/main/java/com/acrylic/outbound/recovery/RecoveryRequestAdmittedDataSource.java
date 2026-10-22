package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * The primary data source of an admitted application. Every {@link #getConnection()} must resolve to
 * a root that this thread is dispatching for; there is no connection-level fallback, so a missing
 * context can never be read as permission.
 *
 * <table>
 * <caption>Resolution</caption>
 * <tr><td>the thread's root is live</td><td>borrow a connection and register {@code CONNECTION}</td></tr>
 * <tr><td>the ambient request carries a root but the thread is unbound</td>
 *     <td>{@code RECOVERY_OPERATION_CONTEXT_LOST}</td></tr>
 * <tr><td>neither a thread binding nor any root marker</td><td>{@code RECOVERY_OPERATION_REQUIRED}</td></tr>
 * <tr><td>the token no longer resolves to a root</td><td>{@code CONTROL_OPERATION_RELEASED}</td></tr>
 * <tr><td>the sub-operation is outside the root's set</td><td>{@code CONTROL_SUBOPERATION_FORBIDDEN}</td></tr>
 * <tr><td>the root is fenced</td><td>{@code RECOVERY_LEASE_FENCED}</td></tr>
 * </table>
 *
 * <p>The shared writer lease belongs to the root, not to the connection: the root takes one
 * reference when it is registered, every borrowed connection takes its own reference, and the
 * connection releases it when it reaches its terminal state. A poisoned or fenced lease therefore
 * refuses new borrows for the whole request, and a request that is still serving keeps the lease
 * while its final session save runs.
 *
 * <p>The legacy connection-level adapter {@link RecoveryAdmittedDataSource} is deliberately not
 * reachable from here: callers that must write without an operation root belong to the explicit
 * offline/test capability set, not to a request path.
 */
public final class RecoveryRequestAdmittedDataSource implements DataSource {
    private final DataSource delegate;
    private final RecoveryOperationRegistry operations;
    private final RecoveryEffectGate effects;
    private final ConnectionAdmission admission;

    /** Runs against this borrow's physical connection, before any connection is exposed. */
    @FunctionalInterface
    public interface ConnectionAdmission { void verify(Connection physical) throws SQLException; }

    /** Explicit connection checks; supplying a closed-chain check never grants effect permission. */
    public record Checks(RecoveryEffectGate effects, ConnectionAdmission admission) {
        public Checks {
            Objects.requireNonNull(effects);
            Objects.requireNonNull(admission);
        }
    }

    /** Without an attached gate every JDBC effect is unfrozen: the default wiring never opts in. */
    public RecoveryRequestAdmittedDataSource(DataSource delegate, RecoveryOperationRegistry operations) {
        this(delegate, operations, RecoveryEffectGate.disabled());
    }

    public RecoveryRequestAdmittedDataSource(DataSource delegate, RecoveryOperationRegistry operations,
            RecoveryEffectGate effects) {
        this(delegate, operations, new Checks(effects, physical -> { }));
    }

    /** Explicit adapter capability. Spring entry wiring still uses the existing effect-only checks. */
    public RecoveryRequestAdmittedDataSource(DataSource delegate, RecoveryOperationRegistry operations,
            Checks checks) {
        this.delegate = Objects.requireNonNull(delegate);
        this.operations = Objects.requireNonNull(operations);
        this.effects = checks.effects();
        this.admission = checks.admission();
    }

    @Override public Connection getConnection() throws SQLException {
        return admit(delegate::getConnection);
    }

    @Override public Connection getConnection(String user, String password) throws SQLException {
        return admit(() -> delegate.getConnection(user, password));
    }

    @FunctionalInterface
    private interface ConnectionSupplier { Connection get() throws SQLException; }

    private Connection admit(ConnectionSupplier supplier) throws SQLException {
        RecoveryOperationLease root = requireRoot();
        RecoverySubLease connection = root.registerSubOperation(RecoverySubOperation.CONNECTION);
        RecoveryWriterLease writer;
        try {
            writer = root.writerReference();
        } catch (RuntimeException | Error error) {
            connection.close();
            throw error;
        }
        Connection physical;
        try {
            physical = supplier.get();
        } catch (SQLException | RuntimeException | Error error) {
            release(writer, connection, error);
            throw error;
        }
        if (physical == null) {
            var error = new SQLException("CONTROL_CONNECTION_UNAVAILABLE");
            release(writer, connection, error);
            throw error;
        }
        try {
            admission.verify(physical);
            var handler = new RecoveryConnectionHandler(physical, writer, RecoveryAdmissionProbe.NOOP, connection);
            // The gate is attached per connection: every effect of this borrow is checked before it
            // reaches the driver, and an unclassified or fenced instance refuses instead of writing.
            handler.useEffectGate(effects);
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, handler);
        } catch (SQLException | RuntimeException | Error error) {
            try { physical.close(); }
            catch (SQLException | RuntimeException | Error close) {
                if (close != error) error.addSuppressed(close);
                retainUnconfirmed(root, writer, error);
                throw error;
            }
            release(writer, connection, error);
            throw error;
        }
    }

    /** Unknown close keeps the child and writer held; root retirement must report UNKNOWN. */
    private void retainUnconfirmed(RecoveryOperationLease root, RecoveryWriterLease writer, Throwable original) {
        writer.poison();
        root.fence();
        try { effects.poison(); }
        catch (RuntimeException | Error notification) {
            if (notification != original) original.addSuppressed(notification);
        }
    }

    /** A borrow that never reached a connection must not leave the root's retained work behind. */
    private static void release(RecoveryWriterLease writer, RecoverySubLease connection, Throwable original) {
        try { writer.close(); } catch (IOException close) { original.addSuppressed(close); }
        connection.close();
    }

    private RecoveryOperationLease requireRoot() {
        RecoveryOperationToken bound = operations.bound(Thread.currentThread());
        if (bound == null) {
            if (operations.ambient() != null) throw new RecoveryControlException("RECOVERY_OPERATION_CONTEXT_LOST");
            throw new RecoveryControlException("RECOVERY_OPERATION_REQUIRED");
        }
        RecoveryOperationLease root = operations.live(bound);
        if (root == null) throw new RecoveryControlException("CONTROL_OPERATION_RELEASED");
        if (root.phase() == RecoveryOperationPhase.FENCED) throw new RecoveryControlException("RECOVERY_LEASE_FENCED");
        return root;
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
