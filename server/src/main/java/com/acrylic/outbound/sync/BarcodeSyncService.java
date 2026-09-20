package com.acrylic.outbound.sync;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.PdaAccess;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class BarcodeSyncService {
    private final PdaAccess access;
    private final BarcodeStreamStore store;
    private final BarcodeBaselineStore baselines;
    public BarcodeSyncService(PdaAccess access,BarcodeStreamStore store,BarcodeBaselineStore baselines) {
        this.access=access;this.store=store;this.baselines=baselines;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Data barcodes(Context context) {
        var principal=access.barcodes(context.epoch());
        if(context.limit()<1 || context.limit()>200) throw new BusinessException(400,"INVALID_LIMIT","同步每页最多200条");
        var ns=new BarcodeCursor.Namespace(context.epoch(),principal.warehouseId());
        if(!context.baselineId().isBlank()) {
            if(!context.cursor().isBlank()) throw invalidCursor();
            return baseline(context,ns);
        }
        if(!context.baselineCursor().isBlank()) throw invalidCursor();
        var published=store.publish(ns);
        if(!context.cursor().isBlank()) return delta(context,ns,published);
        UUID id=baselines.create(ns,published.highWatermark());
        return baselinePage(id,new BarcodeBaselineStore.Meta(ns,published.highWatermark(),null),context);
    }
    private Data delta(Context context,BarcodeCursor.Namespace ns,BarcodeStreamStore.Published published) {
        long cursor;
        try { cursor=BarcodeCursor.parse(context.cursor(),ns); }
        catch(IllegalArgumentException error) { throw invalidCursor(); }
        if(cursor>published.highWatermark()) throw invalidCursor();
        var rows=store.eventsAfter(ns,cursor,context.limit());
        boolean more=rows.size()>context.limit() || published.pending();
        if(rows.size()>context.limit()) rows=rows.subList(0,context.limit());
        long next=rows.isEmpty()?cursor:rows.getLast().sequence();
        return new Data("BARCODE",ns.epoch(),ns.warehouse().toString(),null,null,List.of(),true,Long.toString(published.highWatermark()),
            BarcodeCursor.encode(ns,next),more,rows.stream().map(Event::from).toList());
    }
    private Data baseline(Context context,BarcodeCursor.Namespace ns) {
        UUID id;
        try { id=UUID.fromString(context.baselineId()); }
        catch(IllegalArgumentException error) { throw new BusinessException(400,"INVALID_BASELINE","基线标识无效"); }
        var meta=baselines.meta(id);
        if(meta==null || !meta.namespace().equals(ns) || !meta.expiresAt().isAfter(java.time.Instant.now()))
            throw new BusinessException(409,"SYNC_BASELINE_EXPIRED","条码状态基线已失效，请重新取得基线");
        return baselinePage(id,meta,context);
    }
    private Data baselinePage(UUID id,BarcodeBaselineStore.Meta meta,Context context) {
        UUID cursor;
        try { cursor=BaselineCursor.parse(context.baselineCursor(),id); }
        catch(IllegalArgumentException error) { throw new BusinessException(400,"INVALID_BASELINE_CURSOR",error.getMessage()); }
        var rows=baselines.page(id,cursor,context.limit());boolean complete=rows.size()<=context.limit();
        if(!complete) rows=rows.subList(0,context.limit());
        var ns=meta.namespace();
        return new Data("BARCODE",ns.epoch(),ns.warehouse().toString(),id.toString(),
            complete?null:BaselineCursor.encode(id,rows.getLast().baleId()),rows.stream().map(Baseline::from).toList(),complete,
            Long.toString(meta.highWatermark()),complete?BarcodeCursor.encode(ns,meta.highWatermark()):null,true,List.of());
    }
    private BusinessException invalidCursor() { return new BusinessException(400,"INVALID_SYNC_CURSOR","条码同步游标无效或不属于当前仓库及恢复代次"); }
    public record Context(String epoch,String cursor,String baselineId,String baselineCursor,int limit) { }
    public record Data(String streamType,String recoveryEpoch,String warehouseId,String baselineId,String baselineNextCursor,
        List<Baseline> baseline,boolean baselineComplete,String highWatermark,String nextCursor,boolean hasMore,List<Event> events) { }
    public record Baseline(String baleId,String barcode,String status,String dispatchCycle,String rowVersion,String updatedAt) {
        static Baseline from(BarcodeStreamStore.Item row) {
            return new Baseline(row.baleId().toString(),row.barcode(),row.status(),Long.toString(row.dispatchCycle()),Long.toString(row.rowVersion()),row.updatedAt().toString());
        }
    }
    public record Event(String eventId,String sequence,String baleId,String barcode,String status,String dispatchCycle,String entityVersion,String createdAt) {
        static Event from(BarcodeStreamStore.Event event) {
            var row=event.item();return new Event(event.id(),Long.toString(event.sequence()),row.baleId().toString(),row.barcode(),row.status(),
                Long.toString(row.dispatchCycle()),Long.toString(row.rowVersion()),row.updatedAt().toString());
        }
    }
}
