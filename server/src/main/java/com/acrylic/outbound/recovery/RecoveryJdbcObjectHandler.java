package com.acrylic.outbound.recovery;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;

/** Keeps statements, result sets and metadata inside the admitted connection boundary. */
final class RecoveryJdbcObjectHandler implements InvocationHandler {
    private final Object delegate;
    private final Object parent;
    private final RecoveryConnectionHandler owner;

    RecoveryJdbcObjectHandler(Object delegate, Object parent, RecoveryConnectionHandler owner) {
        this.delegate = delegate; this.parent = parent; this.owner = owner;
    }

    @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if (method.getDeclaringClass() == Object.class) return invokeObject(proxy, method, args);
        if (name.equals("unwrap")) return unwrap(proxy, (Class<?>) args[0]);
        if (name.equals("isWrapperFor")) return ((Class<?>) args[0]).isInstance(proxy);
        // Statement, result-set and metadata calls take the same monitor as the connection handler.
        // Per-connection serialization is the whole point of the adapter boundary: without it an
        // execution can overlap a connection-level close and the recorded transaction/poison state
        // is neither ordered nor visible to that close.
        synchronized (owner) {
            if (name.equals("close")) return invokeClose(method, args);
            owner.assertUsable();
            owner.beforeJdbcCall(name, args);
            owner.probeDelegateCall();
            try {
                Object result = method.invoke(delegate, args);
                owner.afterJdbcCall(name, sql(args));
                if (name.equals("getConnection")) return parent;
                return RecoveryJdbcProxy.wrap(result, parent, owner);
            } catch (Throwable error) {
                Throwable cause = unwrap(error);
                owner.jdbcFailure(name, sql(args), cause);
                throw cause;
            }
        }
    }

    private Object invokeClose(Method method, Object[] args) throws Throwable {
        try { return method.invoke(delegate, args); }
        catch (Throwable error) { throw unwrap(error); }
    }

    private Object unwrap(Object proxy, Class<?> type) throws SQLException {
        if (type.isInstance(proxy)) return proxy;
        throw new SQLException("CONTROL_JDBC_OBJECT_UNWRAP_FORBIDDEN");
    }

    private static Object invokeObject(Object proxy, Method method, Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "toString" -> "RecoveryJdbcProxy(" + proxy.getClass().getInterfaces()[0].getSimpleName() + ")";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null ? null : args[0]);
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }

    private static String sql(Object[] args) {
        return args != null && args.length > 0 && args[0] instanceof String text ? text : null;
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof InvocationTargetException invocation ? invocation.getCause() : error;
    }
}
