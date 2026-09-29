package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Keeps the external writer lease until connection completion is known. Every call on this
 * connection and on the statements, result sets and metadata it handed out takes this instance's
 * monitor, so an admitted execution and a connection-level close cannot overlap and the recorded
 * transaction/poison state is visible to the close that decides whether to release the lease.
 *
 * <p>When the connection belongs to an admitted operation root, its retained {@code CONNECTION}
 * sub-operation is released as well, at the same terminal point, so a root never keeps counting a
 * connection that is already gone.
 */
final class RecoveryConnectionHandler implements InvocationHandler {
    private final Connection delegate;
    private final RecoveryWriterLease lease;
    private final RecoveryAdmissionProbe probe;
    private final RecoverySubLease connectionOperation;
    private boolean autoCommit;
    private boolean transactionActive;
    private boolean poisoned;
    private boolean closed;
    private RecoveryEffectGate effects = RecoveryEffectGate.disabled();
    private boolean effectClean;
    private final java.util.Map<Object, Object> effectChildren = new java.util.IdentityHashMap<>();

    RecoveryConnectionHandler(Connection delegate, RecoveryWriterLease lease) throws SQLException {
        this(delegate, lease, RecoveryAdmissionProbe.NOOP);
    }

    RecoveryConnectionHandler(Connection delegate, RecoveryWriterLease lease, RecoveryAdmissionProbe probe) throws SQLException {
        this(delegate, lease, probe, null);
    }

    /**
     * @param connectionOperation the retained sub-operation this connection belongs to, released when
     *     the connection reaches its terminal state; {@code null} on the legacy connection-level path,
     *     which has no operation root above it.
     */
    RecoveryConnectionHandler(Connection delegate, RecoveryWriterLease lease, RecoveryAdmissionProbe probe,
            RecoverySubLease connectionOperation) throws SQLException {
        this.delegate = delegate; this.lease = lease; this.probe = probe;
        this.connectionOperation = connectionOperation;
        try {
            autoCommit = delegate.getAutoCommit();
            transactionActive = !autoCommit;
        } catch (SQLException error) {
            poison("CONTROL_CONNECTION_STATE_UNCONFIRMED", error);
            throw error;
        }
    }

    @Override public synchronized Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if (method.getDeclaringClass() == Object.class) return invokeObject(proxy, method, args);
        if (name.equals("unwrap")) return unwrap(proxy, (Class<?>) args[0]);
        if (name.equals("isWrapperFor")) return ((Class<?>) args[0]).isInstance(proxy);
        if (name.equals("isClosed")) return isClosed();
        if (name.equals("close")) return close();
        assertUsable();
        beforeConnectionEffect(name, args);
        probe.aroundDelegateCall();
        try {
            Object result = method.invoke(delegate, args);
            afterSuccess(name, args);
            if (effects.enabled() && (name.equals("prepareStatement") || name.equals("prepareCall")))
                return RecoveryJdbcProxy.prepared(result, proxy, this, (String) args[0]);
            return RecoveryJdbcProxy.wrap(result, proxy, this);
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            if (ambiguous(name, cause)) poison("CONTROL_CONNECTION_OUTCOME_UNCONFIRMED", cause);
            throw cause;
        }
    }

    synchronized void assertUsable() throws SQLException {
        if (closed) throw new SQLException("CONTROL_CONNECTION_CLOSED");
        if (poisoned) throw new SQLException(effects.enabled() ? "RECOVERY_LEASE_FENCED" : "CONTROL_CONNECTION_POISONED");
    }

    synchronized void beforeJdbcCall(String name, Object[] args) throws SQLException { assertUsable(); }

    void useEffectGate(RecoveryEffectGate gate) { effects = gate; }
    boolean effectsEnabled() { return effects.enabled(); }
    void checkEffect() throws SQLException { effects.check(); }
    Object knownChild(Object value) { return effectChildren.get(value); }
    void rememberChild(Object value, Object proxy) { effectChildren.put(value, proxy); }

    void startingJdbcCall(String name) {
        if (effects.enabled() && (name.startsWith("execute") || name.startsWith("update")
                || name.equals("insertRow") || name.equals("deleteRow"))) effectClean = false;
    }

    private void beforeConnectionEffect(String name, Object[] args) throws SQLException {
        if (!effects.enabled()) return;
        if (name.equals("commit")) effects.check();
        if (name.equals("setAutoCommit") && Boolean.TRUE.equals(args[0]) && !effectClean) effects.check();
        if (name.equals("beginRequest") || name.equals("endRequest") || name.contains("ShardingKey"))
            throw RecoveryEffectGate.unclassified();
    }

    /** Test seam: the probe hook is shared by the connection path and the JDBC child objects. */
    void probeDelegateCall() { probe.aroundDelegateCall(); }

    synchronized void afterJdbcCall(String name, String sql) {
        if (effects.enabled() && name.startsWith("execute")) effectClean = false;
        if (effects.enabled() && !autoCommit && (name.equals("insertRow") || name.equals("updateRow")
                || name.equals("deleteRow"))) transactionActive = true;
        if (!isExecution(name)) return;
        String command = sql == null ? "" : sql.trim().toUpperCase();
        if (isCommitSql(command) || isRollbackSql(command)) transactionActive = false;
        if (!autoCommit || isBeginSql(command)) transactionActive = true;
    }

    synchronized void jdbcFailure(String name, String sql, Throwable error) {
        if (error instanceof SQLException sqlError && knownStatementFailure(sqlError)) return;
        poison("CONTROL_CONNECTION_OUTCOME_UNCONFIRMED", error);
    }

    private void afterSuccess(String name, Object[] args) {
        if (name.equals("commit") || (name.equals("rollback") && (args == null || args.length == 0)))
            effectClean = true;
        if (name.equals("setAutoCommit") && Boolean.FALSE.equals(args[0])) effectClean = false;
        if (name.equals("setAutoCommit")) {
            autoCommit = (Boolean) args[0];
            if (autoCommit) transactionActive = false;
            return;
        }
        if (name.equals("commit")) { transactionActive = false; return; }
        if (name.equals("rollback") && (args == null || args.length == 0)) transactionActive = false;
        if (name.equals("abort")) poison("CONTROL_CONNECTION_ABORT_UNCONFIRMED", null);
    }

    private Object close() throws SQLException {
        if (closed) return null;
        SQLException failure = null;
        if (transactionActive && !poisoned) {
            try { delegate.rollback(); transactionActive = false; }
            catch (SQLException error) { poison("CONTROL_CONNECTION_ROLLBACK_UNCONFIRMED", error); failure = error; }
        }
        try { delegate.close(); }
        catch (SQLException error) { poison("CONTROL_CONNECTION_CLOSE_UNCONFIRMED", error); failure = combine(failure, error); }
        closed = true;
        endConnectionOperation();
        if (poisoned) {
            if (failure != null) throw failure;
            return null;
        }
        try { lease.close(); }
        catch (IOException error) { poison("CONTROL_WRITER_RELEASE_UNCONFIRMED", error); throw new SQLException(error); }
        return null;
    }

    /** The connection is terminal, so the retained sub-operation under this root ends with it. */
    private void endConnectionOperation() {
        if (connectionOperation == null) return;
        try { connectionOperation.close(); }
        catch (RuntimeException error) { poison("CONTROL_SUBLEASE_RELEASE_UNCONFIRMED", error); }
    }

    private void poison(String code, Throwable cause) {
        poisoned = true;
        lease.poison();
        effects.poison();
    }

    private static SQLException combine(SQLException first, SQLException second) {
        if (first == null) return second;
        first.addSuppressed(second); return first;
    }

    private static boolean ambiguous(String name, Throwable error) {
        if (name.equals("commit") || name.equals("rollback") || name.equals("abort") || name.equals("close")) return true;
        if (name.equals("setAutoCommit")) return true;
        return error instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("08");
    }

    private static boolean knownStatementFailure(SQLException error) {
        String state = error.getSQLState();
        return state != null && (state.startsWith("23") || state.startsWith("42"));
    }

    private static boolean isExecution(String name) {
        return name.startsWith("execute") || name.equals("addBatch");
    }

    private static boolean isBeginSql(String sql) { return sql.startsWith("BEGIN") || sql.startsWith("START TRANSACTION"); }
    private static boolean isCommitSql(String sql) { return sql.startsWith("COMMIT"); }
    private static boolean isRollbackSql(String sql) { return sql.startsWith("ROLLBACK") && !sql.startsWith("ROLLBACK TO"); }

    private Object unwrap(Object proxy, Class<?> type) throws SQLException {
        if (type.isInstance(proxy)) return proxy;
        throw new SQLException("CONTROL_CONNECTION_UNWRAP_FORBIDDEN");
    }

    private boolean isClosed() throws SQLException {
        if (closed) return true;
        try { return delegate.isClosed(); }
        catch (SQLException error) {
            poison("CONTROL_CONNECTION_STATE_UNCONFIRMED", error);
            throw error;
        }
    }

    private static Object invokeObject(Object proxy, Method method, Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "toString" -> "RecoveryConnectionProxy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null ? null : args[0]);
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof InvocationTargetException invocation ? invocation.getCause() : error;
    }
}
