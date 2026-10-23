package com.acrylic.outbound.recovery;

import java.lang.reflect.*;
import java.sql.*;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import static org.assertj.core.api.Assertions.assertThat;

/** Test wire seam below the product connection proxy; only one real export completion is selected. */
final class RecoveryCommitFaultDataSource extends DelegatingDataSource {
    private final RecoveryCommitFaultBoundary boundary;

    RecoveryCommitFaultDataSource(DataSource delegate, RecoveryCommitFaultBoundary boundary) {
        super(delegate); this.boundary = boundary;
    }

    @Override public Connection getConnection() throws SQLException { return wrap(super.getConnection()); }
    @Override public Connection getConnection(String user, String password) throws SQLException {
        return wrap(super.getConnection(user, password));
    }

    private Connection wrap(Connection connection) {
        var state = new InvocationHandler() {
            String selected;
            @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getName().equals("commit") && selected != null) boundary.before(connection, selected);
                Object value = call(connection, method, args);
                if (method.getName().equals("commit") && selected != null) {
                    boundary.committed(connection, selected);
                    throw new SQLException("Synthetic lost commit acknowledgement after real product completion", "08007");
                }
                if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
                        && sql.stripLeading().startsWith("UPDATE background_job SET status='SUCCEEDED'"))
                    return completion((PreparedStatement) value, id -> selected = id);
                return value;
            }
        };
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, state);
    }

    private PreparedStatement completion(PreparedStatement statement, java.util.function.Consumer<String> selected) {
        var state = new InvocationHandler() {
            byte[] job;
            @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getName().startsWith("set") && args != null && args.length > 1
                        && Integer.valueOf(1).equals(args[0]) && args[1] instanceof byte[] bytes) job = bytes.clone();
                Object value = call(statement, method, args);
                if (method.getName().equals("executeUpdate") && boundary.select()) {
                    assertThat(value).isEqualTo(1); assertThat(job).hasSize(16);
                    var bytes = java.nio.ByteBuffer.wrap(job);
                    selected.accept(new java.util.UUID(bytes.getLong(), bytes.getLong()).toString());
                }
                return value;
            }
        };
        return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
            new Class<?>[]{PreparedStatement.class}, state);
    }

    private static Object call(Object delegate, Method method, Object[] args) throws Throwable {
        try { return method.invoke(delegate, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
}
