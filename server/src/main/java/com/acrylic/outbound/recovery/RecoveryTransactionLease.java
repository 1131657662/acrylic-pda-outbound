package com.acrylic.outbound.recovery;

import java.io.IOException;
import org.springframework.transaction.support.*;

/** Unknown commit/rollback outcomes retain the kernel lease until the process is fenced. */
final class RecoveryTransactionLease implements AutoCloseable, TransactionSynchronization {
    private final RecoveryWriterLease writer;
    private final Runnable poison;
    private boolean started;
    private int completion = STATUS_UNKNOWN;

    RecoveryTransactionLease(RecoveryWriterLease writer, Runnable poison) {
        this.writer = writer; this.poison = poison;
    }

    void started() {
        TransactionSynchronizationManager.registerSynchronization(this);
        started = true;
    }

    @Override public void afterCompletion(int status) { completion = status; }

    @Override public int getOrder() { return Integer.MIN_VALUE; }

    @Override public void suspend() {
        // A second manager must not hide an independent transaction's unknown outcome from this lease.
        throw new RecoveryControlException("CONTROL_NESTED_TRANSACTION_FORBIDDEN");
    }

    @Override public void close() throws IOException {
        if (started && completion != STATUS_COMMITTED && completion != STATUS_ROLLED_BACK) {
            writer.poison();
            poison.run();
            throw new RecoveryControlException("CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
        }
        writer.close();
    }
}
