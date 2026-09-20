package com.acrylic.outbound.sync;

import com.acrylic.outbound.common.Ids;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Called after stock mutations, inside their original business transaction. */
public final class BarcodeChanges {
    private BarcodeChanges() { }
    public static void bale(JdbcTemplate jdbc, UUID bale) { mark(jdbc, "b.id=?", bale); }
    public static void warehouse(JdbcTemplate jdbc, UUID warehouse) { mark(jdbc, "b.warehouse_id=?", warehouse); }
    private static void mark(JdbcTemplate jdbc, String predicate, UUID id) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("条码变更必须属于业务事务");
        jdbc.update("""
            INSERT INTO barcode_sync_pending(recovery_epoch,warehouse_id,bale_id,row_version)
            SELECT s.recovery_epoch,b.warehouse_id,b.id,1 FROM bale b CROSS JOIN server_state s
            WHERE s.id=1 AND %s ORDER BY b.id
            ON DUPLICATE KEY UPDATE row_version=row_version+1
            """.formatted(predicate), Ids.bytes(id));
    }
}
