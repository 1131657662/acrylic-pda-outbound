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
    private final RecoveryClosedAuthority closedAuthority;
    private final RecoveryControlSnapshot startup;
    private final TransactionTemplate transaction;
    private volatile boolean uncertainCompletion;

    public RecoveryLeasedTransaction(DataSource dataSource, RecoveryControlLocation configured, Path checkpoint) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource);
        closedAuthority = null;
        try {
            location = configured.checked(); trusted = location.checkpoint(checkpoint);
            startup = new RecoveryControlReader(location).read(trusted);
        } catch (IOException error) { throw new RecoveryControlException("CONTROL_AUTHORITY_UNAVAILABLE", error); }
        // Preserve TransactionException/STATUS_UNKNOWN on commit failure. Translating it into a
        // generic DataAccessException can take Spring's ordinary callback-error rollback path.
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    /** Closed recovery transactions require the explicit external session authority. */
    RecoveryLeasedTransaction(DataSource dataSource, RecoveryClosedAuthority authority) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource);
        this.closedAuthority = java.util.Objects.requireNonNull(authority);
        this.location = authority.location();
        this.trusted = authority.checkpoint();
        this.startup = authority.control();
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public RecoveryControlSnapshot snapshot() { return startup; }

    public <T> T execute(TransactionCallback<T> operation) {
        require(!uncertainCompletion, "CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
        require(!TransactionSynchronizationManager.isSynchronizationActive()
            && !TransactionSynchronizationManager.isActualTransactionActive()
            && !TransactionSynchronizationManager.hasResource(dataSource), "CONTROL_OUTER_TRANSACTION_FORBIDDEN");
        try {
            var acquired = acquire();
            try (var lease = new RecoveryTransactionLease(acquired.writer(), this::fenceUnknown,
                status -> { if (closedAuthority != null) closedAuthority.complete(acquired.operation(), status); })) {
            // TransactionTemplate returns only after commit/rollback callbacks and resource cleanup.
            return transaction.execute(status -> {
                lease.started();
                if (closedAuthority != null) RecoveryClosedOperationContext.bind(dataSource,closedAuthority,acquired.operation());
                if (closedAuthority != null) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) { closedAuthority.requireDuring(acquired.operation()); }
                });
                return operation.doInTransaction(status);
            });
            }
        } catch (IOException error) { throw new RecoveryControlException("CONTROL_AUTHORITY_UNAVAILABLE", error); }
    }

    private Acquired acquire() throws IOException {
        if (closedAuthority != null) return acquireClosed();
        var current = location.checked();
        Path witness = current.checkpoint(trusted);
        return RecoveryControlLock.locked(current.root(), () -> {
            require(startup.equals(RecoveryControlHistory.load(current, witness, RecoveryControlPurpose.NORMAL_ADMISSION)), "CONTROL_INSTANCE_STALE");
            return new Acquired(RecoveryWriterLease.acquire(current.root()), null);
        });
    }

    private void fenceUnknown() {
        uncertainCompletion = true;
        if (closedAuthority != null) closedAuthority.fenceUnknown();
    }

    private Acquired acquireClosed() throws IOException {
        var checked = location.checked();
        return RecoveryControlLock.locked(checked.root(), () -> {
            require(startup.equals(closedAuthority.readLocked()), "CONTROL_INSTANCE_STALE");
            var writer = RecoveryWriterLease.acquireClosed(checked.root());
            try { return new Acquired(writer, closedAuthority.beginLocked()); }
            catch (IOException | RuntimeException error) { writer.close(); throw error; }
        });
    }
    private record Acquired(RecoveryWriterLease writer, RecoveryClosedJournal.Operation operation) { }
}
