package com.acrylic.outbound.recovery;

import java.lang.reflect.*;
import java.sql.*;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.support.*;

/** Mutates a pinned original at beforeCommit before the product's registered revalidation runs. */
final class RecoveryArchiveMutationDataSource extends DelegatingDataSource {
    private final Runnable mutation;
    private boolean registered;
    private boolean mutated;
    RecoveryArchiveMutationDataSource(DataSource database,Runnable mutation) { super(database); this.mutation=mutation; }
    boolean mutated() { return mutated; }
    @Override public Connection getConnection() throws SQLException { return wrap(super.getConnection()); }
    @Override public Connection getConnection(String user,String password) throws SQLException { return wrap(super.getConnection(user,password)); }

    private Connection wrap(Connection connection) {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args) -> {
            if(method.getName().equals("prepareStatement") && TransactionSynchronizationManager.isSynchronizationActive()) register();
            try { return method.invoke(connection,args); }
            catch(InvocationTargetException failure) { throw failure.getCause(); }
        });
    }

    private void register() {
        if(registered) return;
        registered=true;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public int getOrder() { return org.springframework.core.Ordered.HIGHEST_PRECEDENCE; }
            @Override public void beforeCommit(boolean readOnly) { mutated=true; mutation.run(); }
        });
    }
}
