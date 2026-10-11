package com.acrylic.outbound.recovery;

import javax.sql.DataSource;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Transaction-local proof of the actual leased journal operation, unavailable to ordinary transactions. */
final class RecoveryClosedOperationContext {
    private static final Object KEY=new Object();
    static void bind(DataSource database,RecoveryClosedAuthority authority,RecoveryClosedJournal.Operation operation) {
        require(!TransactionSynchronizationManager.hasResource(KEY),"CONTROL_CLOSED_OPERATION_ALREADY_BOUND");
        var context=new Context(database,authority,operation,TransactionSynchronizationManager.getResource(database));
        TransactionSynchronizationManager.bindResource(KEY,context);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { TransactionSynchronizationManager.unbindResource(KEY); }
        });
    }
    static void requireCurrent(DataSource database,RecoveryClosedAuthority authority) {
        Object value=TransactionSynchronizationManager.getResource(KEY);
        require(value instanceof Context,"CONTROL_CLOSED_OPERATION_REQUIRED"); var context=(Context)value;
        require(context.database()==database && context.connection()==TransactionSynchronizationManager.getResource(database)
            && context.authority().control().equals(authority.control())
            && context.authority().authorityRoot().equals(authority.authorityRoot())
            && context.authority().sourceIdentity().equals(authority.sourceIdentity())
            && context.authority().sourcePinSha256().equals(authority.sourcePinSha256()),"CONTROL_CLOSED_OPERATION_MISMATCH");
        authority.requireDuring(context.operation());
    }
    private record Context(DataSource database,RecoveryClosedAuthority authority,RecoveryClosedJournal.Operation operation,Object connection) { }
    private RecoveryClosedOperationContext() { }
}
