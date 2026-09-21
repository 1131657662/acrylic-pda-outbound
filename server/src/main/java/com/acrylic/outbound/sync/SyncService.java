package com.acrylic.outbound.sync;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.PdaAccess;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class SyncService {
    private final PdaAccess access;
    private final IdentityWriteGuard guard;private final com.acrylic.outbound.pda.PdaAuthLocks locks;
    private final SyncStreamStore store;
    private final SyncBaselineStore baselines;
    public SyncService(PdaAccess access, IdentityWriteGuard guard, SyncStreamStore store, SyncBaselineStore baselines,com.acrylic.outbound.pda.PdaAuthLocks locks) {
        this.access = access; this.guard = guard; this.store = store; this.baselines = baselines;this.locks=locks;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Data orders(Context context) {
        var principal = access.require("pda:assign");
        if (context.limit() < 1 || context.limit() > 200) throw new BusinessException(400, "INVALID_LIMIT", "同步每页最多200条");
        guard.lock(context.epoch());
        locks.principal(principal);
        principal = access.require("pda:assign");
        var namespace = new SyncCursor.Namespace(context.epoch(), principal.warehouseId());
        if (!context.baselineId().isBlank()) {
            if (!context.cursor().isBlank()) throw invalidCursor();
            return baseline(context, namespace);
        }
        if (!context.baselineCursor().isBlank()) throw invalidCursor();
        var published = store.publish(namespace);
        if (context.cursor().isBlank()) {
            UUID id = baselines.create(namespace, published.highWatermark());
            return baselinePage(id, new SyncBaselineStore.Meta(namespace, published.highWatermark(), null), context);
        }
        return delta(context, namespace, published);
    }
    private Data delta(Context context, SyncCursor.Namespace namespace, SyncStreamStore.Published published) {
        long cursor;
        try { cursor = SyncCursor.parse(context.cursor(), namespace); }
        catch (IllegalArgumentException error) { throw invalidCursor(); }
        if (cursor > published.highWatermark()) throw invalidCursor();
        var events = store.eventsAfter(namespace, cursor, context.limit());
        boolean more = events.size() > context.limit() || published.pending();
        if (events.size() > context.limit()) events = events.subList(0, context.limit());
        long next = events.isEmpty() ? cursor : events.getLast().sequence();
        return new Data("ORDER", namespace.epoch(), namespace.warehouse().toString(), null, null, List.of(), true,
            Long.toString(published.highWatermark()), SyncCursor.encode(namespace, next), more, events.stream().map(Event::from).toList());
    }
    private Data baseline(Context context, SyncCursor.Namespace namespace) {
        UUID id;
        try { id = UUID.fromString(context.baselineId()); }
        catch (IllegalArgumentException error) { throw new BusinessException(400, "INVALID_BASELINE", "基线标识无效"); }
        var meta = baselines.meta(id);
        if (meta == null || !meta.namespace().equals(namespace) || !meta.expiresAt().isAfter(java.time.Instant.now()))
            throw new BusinessException(409, "SYNC_BASELINE_EXPIRED", "订单基线已失效，请重新取得基线");
        return baselinePage(id, meta, context);
    }
    private Data baselinePage(UUID id, SyncBaselineStore.Meta meta, Context context) {
        UUID cursor;
        try { cursor = BaselineCursor.parse(context.baselineCursor(), id); }
        catch (IllegalArgumentException error) { throw new BusinessException(400, "INVALID_BASELINE_CURSOR", error.getMessage()); }
        var rows = baselines.page(id, cursor, context.limit());
        boolean complete = rows.size() <= context.limit();
        if (!complete) rows = rows.subList(0, context.limit());
        var namespace = meta.namespace();
        return new Data("ORDER", namespace.epoch(), namespace.warehouse().toString(), id.toString(),
            complete ? null : BaselineCursor.encode(id, rows.getLast().orderId()), rows.stream().map(Baseline::from).toList(),
            complete, Long.toString(meta.highWatermark()), complete ? SyncCursor.encode(namespace, meta.highWatermark()) : null,
            true, List.of());
    }
    private BusinessException invalidCursor() { return new BusinessException(400, "INVALID_SYNC_CURSOR", "同步游标无效或不属于当前仓库及恢复代次"); }
    public record Context(String epoch, String cursor, String baselineId, String baselineCursor, int limit) { }
    public record Data(String streamType, String recoveryEpoch, String warehouseId, String baselineId, String baselineNextCursor,
        List<Baseline> baseline, boolean baselineComplete, String highWatermark, String nextCursor, boolean hasMore, List<Event> events) { }
    public record Baseline(String orderId, String status, String rowVersion, String updatedAt) {
        static Baseline from(SyncBaselineStore.Item row) {
            return new Baseline(row.orderId().toString(), row.status(), Long.toString(row.rowVersion()), row.updatedAt().toString());
        }
    }
    public record Event(String eventId, String sequence, String orderId, String entityVersion, String eventType, String status, String createdAt) {
        static Event from(SyncStreamStore.Event row) {
            return new Event(row.id().toString(), Long.toString(row.sequence()), row.orderId().toString(), Long.toString(row.entityVersion()),
                row.eventType(), row.status(), row.createdAt().toString());
        }
    }
}
