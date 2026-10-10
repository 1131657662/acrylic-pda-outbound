package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.BiFunction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Keeps a pinned native verification unchanged through a trusted transaction-bound consumer. */
final class RecoveryTemporaryNativeArchiveConsumer {
    private final JdbcTemplate jdbc;
    RecoveryTemporaryNativeArchiveConsumer(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    <T> T consume(RecoveryRecoveredTemporaryArchive archive, JsonNode input,
            BiFunction<RecoveryRecoveredTemporaryArchive,RecoveryTemporaryNativeHandoverFacts.Facts,T> consumer) {
        var verified = RecoveryTemporaryNativeHandoverFacts.readVerified(archive, input, jdbc);
        T result = consumer.apply(archive, verified.facts());
        Runnable recheck = () -> require(verified.equals(RecoveryTemporaryNativeHandoverFacts.readVerified(archive, input, jdbc)),
            "RECOVERY_NATIVE_VERIFICATION_CHANGED");
        recheck.run();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) { recheck.run(); }
        });
        return result;
    }
}
