package com.acrylic.outbound.recovery;

import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/**
 * Offline reconstruction seam only. Callers must prove original occupancy/permission and business
 * facts before supplying DML. No Spring bean, HTTP route, normal admission or release is provided.
 */
public final class RecoveryClosedTransaction {
    private final RecoveryClosedAuthority authority;
    private final RecoveryLeasedTransaction transaction;
    private final RecoveryClosedWriteProjection projection;

    public RecoveryClosedTransaction(DataSource dataSource, RecoveryClosedAuthority authority) {
        this.authority = Objects.requireNonNull(authority);
        authority.read();
        require(Set.of("RECOVERING", "VERIFYING").contains(authority.control().phase()), "CONTROL_CLOSED_WRITE_PHASE");
        transaction = new RecoveryLeasedTransaction(dataSource, authority);
        projection = new RecoveryClosedWriteProjection(new JdbcTemplate(dataSource));
    }

    public <T> T execute(RecoveryPinnedSource source, TransactionCallback<T> operation) {
        require(source != null && source.control().equals(authority.control())
            && source.manifestSha256().equals(authority.sourcePinSha256())
            && source.identity().equals(authority.sourceIdentity())
            && authority.control().affectedWarehouseIds().contains(source.identity().warehouseId().toString()),
            "CONTROL_CLOSED_SOURCE_MISMATCH");
        Objects.requireNonNull(operation);
        return transaction.execute(status -> {
            projection.requireMatches(authority.control());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) {
                    projection.requireMatches(authority.control());
                }
            });
            return operation.doInTransaction(status);
        });
    }
}
