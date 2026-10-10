package com.acrylic.outbound.recovery;

import java.lang.reflect.*;
import java.sql.*;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/** Test-only filesystem mutation after the real last reservation INSERT, before commit revalidation. */
final class RecoveryTemporaryEvidenceMutationDataSource extends DelegatingDataSource {
    private final Runnable mutation;
    private boolean mutated;
    RecoveryTemporaryEvidenceMutationDataSource(DataSource source,Runnable mutation) { super(source); this.mutation=mutation; }
    boolean mutated() { return mutated; }
    @Override public Connection getConnection() throws SQLException { return wrap(super.getConnection()); }
    @Override public Connection getConnection(String user,String password) throws SQLException { return wrap(super.getConnection(user,password)); }
    private Connection wrap(Connection connection) {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args) -> {
            Object result=invoke(connection,method,args);
            if(method.getName().equals("prepareStatement") && args[0] instanceof String sql && sql.startsWith("INSERT INTO bale_commitment"))
                return statement((PreparedStatement)result);
            return result;
        });
    }
    private PreparedStatement statement(PreparedStatement statement) {
        return (PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(proxy,method,args) -> {
            Object result=invoke(statement,method,args);
            if(method.getName().equals("executeUpdate") && !mutated) { mutated=true; mutation.run(); } return result;
        });
    }
    private static Object invoke(Object receiver,Method method,Object[] arguments) throws Throwable {
        try { return method.invoke(receiver,arguments); } catch(InvocationTargetException error) { throw error.getCause(); }
    }
}
