package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Lifecycle primitive only. Callers must check capability and DB projection before any DML.
 * Not a Spring bean or a replacement for ordinary/evidence admission. */
public final class RecoveryLeasedTransaction {
    private final DataSource dataSource;
    private final RecoveryControlLocation location;
    private final Path trusted;
    private final RecoveryControlSnapshot startup;
    private final TransactionTemplate transaction;
    private volatile boolean uncertainCompletion;

    public RecoveryLeasedTransaction(DataSource dataSource, RecoveryControlLocation configured, Path checkpoint) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource);
        try {
            location = configured.checked(); trusted = location.checkpoint(checkpoint);
            startup = new RecoveryControlReader(location).read(trusted);
        } catch (IOException error) { throw new RecoveryControlException("CONTROL_AUTHORITY_UNAVAILABLE", error); }
        // Preserve TransactionException/STATUS_UNKNOWN on commit failure. Translating it into a
        // generic DataAccessException can take Spring's ordinary callback-error rollback path.
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public RecoveryControlSnapshot snapshot() { return startup; }

    public <T> T execute(TransactionCallback<T> operation) {
        require(!uncertainCompletion, "CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
        require(!TransactionSynchronizationManager.isSynchronizationActive()
            && !TransactionSynchronizationManager.isActualTransactionActive()
            && !TransactionSynchronizationManager.hasResource(dataSource), "CONTROL_OUTER_TRANSACTION_FORBIDDEN");
        try (var lease = new RecoveryTransactionLease(acquire(), () -> uncertainCompletion = true)) {
            // TransactionTemplate returns only after commit/rollback callbacks and resource cleanup.
            return transaction.execute(status -> { lease.started(); return operation.doInTransaction(status); });
        } catch (IOException error) { throw new RecoveryControlException("CONTROL_AUTHORITY_UNAVAILABLE", error); }
    }

    private RecoveryWriterLease acquire() throws IOException {
        var current = location.checked();
        Path witness = current.checkpoint(trusted);
        return RecoveryControlLock.locked(current.root(), () -> {
            require(startup.equals(RecoveryControlHistory.load(current, witness)), "CONTROL_INSTANCE_STALE");
            return RecoveryWriterLease.acquire(current.root());
        });
    }
}
