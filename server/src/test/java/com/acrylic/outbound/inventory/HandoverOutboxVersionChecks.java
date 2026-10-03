package com.acrylic.outbound.inventory;

import com.acrylic.outbound.handover.HandoverStore;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Isolated arithmetic fixtures roll back; they are not device or business terminalization evidence. */
final class HandoverOutboxVersionChecks {
    private final HandoverStopFixture f;
    private final HandoverStore store;
    private final TransactionTemplate tx;

    HandoverOutboxVersionChecks(WebApplicationContext app, HandoverStopFixture f) {
        this.f = f; store = app.getBean(HandoverStore.class);
        tx = new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    void verify() throws Exception {
        var facts = new HandoverStopFacts(f, f.jdbc); var before = facts.snapshot();
        assertThatThrownBy(() -> store.insertOutbox(UUID.fromString(f.r.f.order), 1, 1000,
            "HANDOVER_STOPPED", "{}", Instant.now())).isInstanceOf(IllegalTransactionStateException.class);
        facts.unchanged(before);
        verifyGap(false); verifyGap(true);
    }

    private void verifyGap(boolean outboxAhead) throws Exception {
        var facts = new HandoverStopFacts(f, f.jdbc); var before = facts.snapshot();
        tx.executeWithoutResult(status -> {
            f.jdbc.queryForObject("SELECT row_version FROM outbound_order WHERE id=UUID_TO_BIN(?) FOR UPDATE", Long.class, f.r.f.order);
            long base = Math.max(orderVersion(), outboxVersion()) + 20;
            if (outboxAhead) f.jdbc.update("""
                INSERT INTO order_sync_outbox(id,recovery_epoch,warehouse_id,order_id,entity_version,event_type,payload,published_seq,created_at)
                SELECT UUID_TO_BIN(?),1,warehouse_id,id,?,'HANDOVER_STOPPED','{}',NULL,UTC_TIMESTAMP(3)
                FROM outbound_order WHERE id=UUID_TO_BIN(?)
                """, UUID.randomUUID().toString(), base, f.r.f.order);
            else f.jdbc.update("UPDATE outbound_order SET row_version=? WHERE id=UUID_TO_BIN(?)", base, f.r.f.order);
            long next = store.nextOutboxEntityVersion(UUID.fromString(f.r.f.order), 1);
            assertThat(next).isEqualTo(base + 1);
            store.insertOutbox(UUID.fromString(f.r.f.order), 1, next, "HANDOVER_STOPPED", "{}", Instant.now());
            assertThat(orderVersion()).isEqualTo(next); assertThat(outboxVersion()).isEqualTo(next);
            status.setRollbackOnly();
        });
        facts.unchanged(before);
    }

    private long orderVersion() {
        return f.jdbc.queryForObject("SELECT row_version FROM outbound_order WHERE id=UUID_TO_BIN(?)", Long.class, f.r.f.order);
    }

    private long outboxVersion() {
        return f.jdbc.queryForObject("SELECT COALESCE(MAX(entity_version),0) FROM order_sync_outbox WHERE order_id=UUID_TO_BIN(?) AND recovery_epoch=1", Long.class, f.r.f.order);
    }
}
