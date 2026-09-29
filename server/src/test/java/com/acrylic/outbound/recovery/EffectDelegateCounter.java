package com.acrylic.outbound.recovery;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;

/** Counts the calls reaching the real driver, below the product gate. */
final class EffectDelegateCounter {
    private final Map<String, Integer> calls = new HashMap<>();
    private final Map<Object, Object> proxies = new IdentityHashMap<>();

    int count(String call) { return calls.getOrDefault(call, 0); }

    Object wrap(Object value) {
        Class<?> type = type(value);
        if (type == null) return value;
        return proxies.computeIfAbsent(value, ignored -> Proxy.newProxyInstance(type.getClassLoader(),
            new Class<?>[]{type}, (proxy, method, args) -> invoke(value, method, args)));
    }

    private Object invoke(Object target, Method method, Object[] args) throws Throwable {
        String name = type(target).getSimpleName() + "." + method.getName();
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
        return null;
    }
}
