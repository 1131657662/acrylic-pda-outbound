package com.acrylic.outbound.inventory;

import com.acrylic.outbound.correction.CorrectionStore;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Deliberate isolated version gaps are rolled back; no synthetic business terminal state is committed. */
final class CorrectionOutboxVersionChecks {
    private final CorrectionAccountingHttpFixture f;
    private final CorrectionStore store;
    private final TransactionTemplate tx;

    CorrectionOutboxVersionChecks(CorrectionAccountingHttpFixture f) {
        this.f = f; store = f.context.getBean(CorrectionStore.class);
        tx = new TransactionTemplate(f.context.getBean(PlatformTransactionManager.class));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    void verify() throws Exception {
        var facts = new CorrectionOutboxFacts(f); var before = facts.snapshot();
        assertThatThrownBy(() -> store.insertOutbox(UUID.fromString(f.order), 1, "CORRECTION_EFFECTIVE", 1000,
            "{}", Instant.now())).isInstanceOf(IllegalTransactionStateException.class);
        facts.unchanged(before, "no-transaction"); gap(false); gap(true);
    }

    private void gap(boolean outboxAhead) throws Exception {
        var facts = new CorrectionOutboxFacts(f); var before = facts.snapshot();
        tx.executeWithoutResult(status -> {
            long row = f.jdbc.queryForObject("SELECT row_version FROM outbound_order WHERE id=UUID_TO_BIN(?) FOR UPDATE", Long.class, f.order);
            long event = f.jdbc.queryForObject("SELECT MAX(entity_version) FROM order_sync_outbox WHERE order_id=UUID_TO_BIN(?) AND recovery_epoch=1", Long.class, f.order);
            long base = Math.max(row, event) + 20;
            if (outboxAhead) f.jdbc.update("""
                INSERT INTO order_sync_outbox(id,recovery_epoch,warehouse_id,order_id,entity_version,event_type,payload,published_seq,created_at)
                SELECT UUID_TO_BIN(?),1,warehouse_id,id,?,'CORRECTION_EFFECTIVE','{}',NULL,UTC_TIMESTAMP(3)
                FROM outbound_order WHERE id=UUID_TO_BIN(?)
                """, UUID.randomUUID().toString(), base, f.order);
            else f.jdbc.update("UPDATE outbound_order SET row_version=? WHERE id=UUID_TO_BIN(?)", base, f.order);
            long next = store.nextOutboxEntityVersion(UUID.fromString(f.order), 1); assertThat(next).isEqualTo(base + 1);
            store.insertOutbox(UUID.fromString(f.order), 1, "CORRECTION_EFFECTIVE", next, "{}", Instant.now());
            assertThat(f.jdbc.queryForObject("SELECT row_version FROM outbound_order WHERE id=UUID_TO_BIN(?)", Long.class, f.order)).isEqualTo(next);
            status.setRollbackOnly();
        });
        facts.unchanged(before, outboxAhead ? "outbox-ahead" : "order-ahead");
    }
}
