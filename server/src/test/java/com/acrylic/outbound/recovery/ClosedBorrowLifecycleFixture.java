package com.acrylic.outbound.recovery;

import java.sql.Connection;
import java.util.UUID;
import javax.sql.DataSource;
import static org.mockito.Mockito.*;

/** Real synthetic authority/writer locks with mocked driver failures, never a production database. */
final class ClosedBorrowLifecycleFixture implements AutoCloseable {
    final RecoveryControlFixture control;
    final RecoveryOperationRegistry registry;
    final RecoveryOperationLease root;
    final DataSource source = mock(DataSource.class);
    final Connection physical = mock(Connection.class);
    final EffectTestAuthority effectAuthority = new EffectTestAuthority();
    final RecoveryEffectGate effects;
    int unknown;

    ClosedBorrowLifecycleFixture() throws Exception {
        control = RecoveryWriterLeaseTest.protocol();
        registry = new RecoveryOperationRegistry(control.location(), control.checkpoint,
            new RecoveryOperationRegistry.LeaseChannel() {
                @Override public UUID publish() { return UUID.randomUUID(); }
                @Override public void unknown() { unknown++; }
            });
        var token = new RecoveryOperationToken(UUID.randomUUID(), RecoveryOperationKind.HTTP_REQUEST,
            control.installation, registry.startup().recordSha256(), null);
        root = RecoveryControlLock.locked(control.root, () -> registry.register(token));
        root.bindDispatch(Thread.currentThread());
        when(source.getConnection()).thenReturn(physical);
        when(physical.getAutoCommit()).thenReturn(true);
        effects = RecoveryEffectGate.enabled(effectAuthority);
        System.out.println("MODEL|borrow-lifecycle|authority=" + control.root);
    }

    RecoveryRequestAdmittedDataSource adapter(RecoveryRequestAdmittedDataSource.ConnectionAdmission admission) {
        return new RecoveryRequestAdmittedDataSource(source, registry,
            new RecoveryRequestAdmittedDataSource.Checks(effects, admission));
    }

    RecoveryWriterLease exclusive() throws Exception {
        return RecoveryControlLock.locked(control.root, () -> RecoveryWriterLease.acquireClosed(control.root));
    }

    @Override public void close() {
        root.unbindAll();
        root.close();
    }
}
