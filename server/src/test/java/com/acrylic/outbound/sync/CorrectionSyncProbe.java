package com.acrylic.outbound.sync;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/** Test bridge to the package-private namespace; uses the real publisher's required locking and isolation. */
public final class CorrectionSyncProbe {
    private CorrectionSyncProbe() { }
    public static void publish(WebApplicationContext app, String warehouse) {
        var tx = new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.executeWithoutResult(status -> {
            app.getBean(JdbcTemplate.class).queryForObject("SELECT recovery_epoch FROM server_state WHERE id=1 FOR SHARE", Long.class);
            app.getBean(SyncStreamStore.class).publish(new SyncCursor.Namespace("1", UUID.fromString(warehouse)));
        });
    }
}
