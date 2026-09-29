package com.acrylic.outbound.recovery;

import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/** Creates standard JDBC proxies without exposing vendor objects. */
final class RecoveryJdbcProxy {
    private RecoveryJdbcProxy() { }

    static Object wrap(Object value, Object parent, RecoveryConnectionHandler owner) {
        if (value == null || !isJdbcObject(value)) return value;
        if (owner.effectsEnabled() && owner.knownChild(value) != null) return owner.knownChild(value);
        Class<?>[] interfaces = interfaces(value);
        Object proxy = Proxy.newProxyInstance(interfaces[0].getClassLoader(), interfaces,
            new RecoveryJdbcObjectHandler(value, parent, owner));
        if (owner.effectsEnabled()) owner.rememberChild(value, proxy);
        return proxy;
    }

    static Object prepared(Object value, Object parent, RecoveryConnectionHandler owner, String sql) {
        Class<?>[] interfaces = interfaces(value);
        var handler = new RecoveryJdbcObjectHandler(value, parent, owner);
        handler.rememberSql(sql);
        Object proxy = Proxy.newProxyInstance(interfaces[0].getClassLoader(), interfaces, handler);
        owner.rememberChild(value, proxy);
        return proxy;
    }

    private static boolean isJdbcObject(Object value) {
        return value instanceof Statement || value instanceof ResultSet ||
            value instanceof DatabaseMetaData;
    }

    private static Class<?>[] interfaces(Object value) {
        if (value instanceof CallableStatement) return new Class<?>[]{CallableStatement.class, PreparedStatement.class, Statement.class};
        if (value instanceof PreparedStatement) return new Class<?>[]{PreparedStatement.class, Statement.class};
        if (value instanceof Statement) return new Class<?>[]{Statement.class};
        if (value instanceof ResultSet) return new Class<?>[]{ResultSet.class};
        return new Class<?>[]{DatabaseMetaData.class};
    }
}
