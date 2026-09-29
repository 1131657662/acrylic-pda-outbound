package com.acrylic.outbound.recovery;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;

/** Counts the calls reaching the real driver, below the product gate. */
final class EffectDelegateCounter {
    private final Map<String, Integer> calls = new HashMap<>();
    private final Map<Object, Object> proxies = new IdentityHashMap<>();
    private final Map<String, SQLException> faults = new HashMap<>();

    int count(String call) { return calls.getOrDefault(call, 0); }
    void failNext(String call, SQLException fault) { faults.put(call, fault); }

    Object wrap(Object value) {
        Class<?> type = type(value);
        if (type == null) return value;
        return proxies.computeIfAbsent(value, ignored -> Proxy.newProxyInstance(type.getClassLoader(),
            new Class<?>[]{type}, (proxy, method, args) -> invoke(value, method, args)));
    }

    private Object invoke(Object target, Method method, Object[] args) throws Throwable {
        String name = type(target).getSimpleName() + "." + method.getName();
        SQLException injected = faults.remove(name);
        if (injected != null) throw injected; // MODEL fault before the real delegate, never labelled REAL.
        calls.merge(name, 1, Integer::sum);
        try { return wrap(method.invoke(target, args)); }
        catch (InvocationTargetException error) { throw error.getCause(); }
    }

    private static Class<?> type(Object value) {
        if (value instanceof Connection) return Connection.class;
        if (value instanceof CallableStatement) return CallableStatement.class;
        if (value instanceof PreparedStatement) return PreparedStatement.class;
        if (value instanceof Statement) return Statement.class;
        if (value instanceof ResultSet) return ResultSet.class;
        if (value instanceof DatabaseMetaData) return DatabaseMetaData.class;
        if (value instanceof NClob) return NClob.class;
        if (value instanceof Blob) return Blob.class;
        if (value instanceof Clob) return Clob.class;
        if (value instanceof SQLXML) return SQLXML.class;
        if (value instanceof java.sql.Array) return java.sql.Array.class;
        if (value instanceof Ref) return Ref.class;
        return null;
    }
}
