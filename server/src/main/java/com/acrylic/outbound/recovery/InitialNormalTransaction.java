package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Dedicated registration transaction. Any unconfirmed outcome permanently fences this store. */
final class InitialNormalTransaction {
    private final TransactionTemplate transaction;
    private final InitialNormalStore store;
    private boolean poisoned;

    InitialNormalTransaction(DataSource source, InitialNormalStore store) {
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        this.store = store;
    }

    <T> T execute(Supplier<T> work) throws IOException {
        require(!poisoned, "INITIAL_NORMAL_UNKNOWN");
        require(!TransactionSynchronizationManager.isActualTransactionActive(), "INITIAL_NORMAL_OUTER_TRANSACTION");
        var outcome = new AtomicInteger(TransactionSynchronization.STATUS_UNKNOWN);
        try {
            return transaction.execute(status -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int value) { outcome.set(value); }
                });
                return work.get();
            });
        } catch (RuntimeException | Error error) {
            if (outcome.get() != TransactionSynchronization.STATUS_ROLLED_BACK) poison(error);
            throw error;
        }
    }

    private void poison(Throwable error) throws IOException {
        poisoned = true;
        try { store.unknownLocked(); }
        catch (IOException | RuntimeException markerFailure) {
            error.addSuppressed(markerFailure);
            throw new RecoveryControlException("INITIAL_NORMAL_UNKNOWN_PERSIST_FAILED", error);
        }
    }
}
